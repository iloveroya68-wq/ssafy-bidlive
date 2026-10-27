package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.chat.domain.ChatDeletedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageDeletionReason;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.NoticeUnpinnedEvent;
import com.ssafy.bidlive.chat.domain.QuestionRemovalReason;
import com.ssafy.bidlive.chat.domain.QuestionRemovedEvent;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 판매자·운영자 삭제 권한, 멱등 결과와 후속 이벤트 계약을 검증한다. */
@ExtendWith(MockitoExtension.class)
class ChatMessageDeletionServiceTest {

	private static final long AUCTION_ID = 42L;
	private static final long MEMBER_ID = 7L;
	private static final ChatMessageId MESSAGE_ID = new ChatMessageId(
		"01J8W3P2N9KQ4T123456789ABC"
	);
	private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

	@Mock
	private ChatMessageDeletionPersistence persistence;
	@Mock
	private AuctionOwnershipReader ownershipReader;
	@Mock
	private MemberPersistence memberPersistence;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private ChatMessageDeletionService service;

	@BeforeEach
	void setUp() {
		service = new ChatMessageDeletionService(
			persistence,
			ownershipReader,
			memberPersistence,
			eventPublisher,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void sellerDeletesMessageAndPublishesLinkedEvents() throws Exception {
		Member seller = member(MemberRole.USER, MemberStatus.ACTIVE);
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(seller));
		when(ownershipReader.isSeller(AUCTION_ID, MEMBER_ID)).thenReturn(true);
		when(persistence.delete(org.mockito.ArgumentMatchers.any()))
			.thenReturn(ChatMessageDeletionResult.deleted(true, true));

		service.deleteByMember(AUCTION_ID, MEMBER_ID, MESSAGE_ID);

		ArgumentCaptor<ChatMessageDeletionCommand> command =
			ArgumentCaptor.forClass(ChatMessageDeletionCommand.class);
		verify(persistence).delete(command.capture());
		assertThat(command.getValue().reason())
			.isEqualTo(ChatMessageDeletionReason.SELLER_DELETED);
		assertThat(command.getValue().deletedByMemberId()).isEqualTo(MEMBER_ID);
		assertThat(command.getValue().deletedAt()).isEqualTo(NOW);
		verify(eventPublisher).publishEvent(
			new ChatDeletedEvent(AUCTION_ID, MESSAGE_ID)
		);
		verify(eventPublisher).publishEvent(new NoticeUnpinnedEvent(AUCTION_ID));
		verify(eventPublisher).publishEvent(new QuestionRemovedEvent(
			AUCTION_ID,
			MESSAGE_ID,
			QuestionRemovalReason.DELETED
		));
	}

	@Test
	void adminUsesLatestDatabaseRoleWithoutOwnershipCheck() throws Exception {
		Member admin = member(MemberRole.ADMIN, MemberStatus.ACTIVE);
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(admin));
		when(persistence.delete(org.mockito.ArgumentMatchers.any()))
			.thenReturn(ChatMessageDeletionResult.deleted(false, false));

		service.deleteByMember(AUCTION_ID, MEMBER_ID, MESSAGE_ID);

		ArgumentCaptor<ChatMessageDeletionCommand> command =
			ArgumentCaptor.forClass(ChatMessageDeletionCommand.class);
		verify(persistence).delete(command.capture());
		assertThat(command.getValue().reason())
			.isEqualTo(ChatMessageDeletionReason.ADMIN_DELETED);
		verify(ownershipReader, never()).isSeller(AUCTION_ID, MEMBER_ID);
	}

	@Test
	void repeatedDeletionSucceedsWithoutEvents() throws Exception {
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(member(MemberRole.ADMIN, MemberStatus.ACTIVE)));
		when(persistence.delete(org.mockito.ArgumentMatchers.any()))
			.thenReturn(ChatMessageDeletionResult.alreadyDeleted());

		service.deleteByMember(AUCTION_ID, MEMBER_ID, MESSAGE_ID);

		verify(eventPublisher, never()).publishEvent(
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void hidesMissingAndCrossAuctionMessageAsNotFound() throws Exception {
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(member(MemberRole.ADMIN, MemberStatus.ACTIVE)));
		when(persistence.delete(org.mockito.ArgumentMatchers.any()))
			.thenReturn(ChatMessageDeletionResult.notFound());

		assertThatThrownBy(() ->
			service.deleteByMember(AUCTION_ID, MEMBER_ID, MESSAGE_ID))
			.isInstanceOfSatisfying(
				ChatMessageDeletionException.class,
				exception -> assertThat(exception.errorCode())
					.isEqualTo(ChatMessageDeletionErrorCode.MESSAGE_NOT_FOUND)
			);
		verify(eventPublisher, never()).publishEvent(
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void rejectsActiveNonSellerAndInactiveMember() throws Exception {
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(member(MemberRole.USER, MemberStatus.ACTIVE)));
		when(ownershipReader.isSeller(AUCTION_ID, MEMBER_ID)).thenReturn(false);

		assertThatThrownBy(() ->
			service.deleteByMember(AUCTION_ID, MEMBER_ID, MESSAGE_ID))
			.isInstanceOfSatisfying(
				ChatMessageDeletionException.class,
				exception -> assertThat(exception.errorCode())
					.isEqualTo(ChatMessageDeletionErrorCode.CHAT_DELETE_FORBIDDEN)
			);

		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(member(MemberRole.USER, MemberStatus.SUSPENDED)));
		assertThatThrownBy(() ->
			service.deleteByMember(AUCTION_ID, MEMBER_ID, MESSAGE_ID))
			.isInstanceOfSatisfying(
				ChatMessageDeletionException.class,
				exception -> assertThat(exception.errorCode())
					.isEqualTo(ChatMessageDeletionErrorCode.UNAUTHORIZED)
			);
		verify(persistence, never()).delete(org.mockito.ArgumentMatchers.any());
	}

	private static Member member(
		MemberRole role,
		MemberStatus status
	) throws Exception {
		Member member = Member.createOAuthMember();
		setField(member, "id", MEMBER_ID);
		setField(member, "role", role);
		setField(member, "status", status);
		return member;
	}

	private static void setField(
		Member member,
		String name,
		Object value
	) throws Exception {
		Field field = Member.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(member, value);
	}
}
