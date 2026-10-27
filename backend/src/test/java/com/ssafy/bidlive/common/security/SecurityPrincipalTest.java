package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityPrincipalTest {

    @Test
    void returnsMemberIdAsPrincipalName() {
        SecurityPrincipal principal =
                new SecurityPrincipal(25L, MemberRole.USER);

        assertThat(principal.getName()).isEqualTo("25");
    }

    @Test
    void retainsMemberIdAndRole() {
        SecurityPrincipal principal =
                new SecurityPrincipal(25L, MemberRole.ADMIN);

        assertThat(principal.memberId()).isEqualTo(25L);
        assertThat(principal.role()).isEqualTo(MemberRole.ADMIN);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsNonPositiveMemberId(long memberId) {
        assertThatThrownBy(
                () -> new SecurityPrincipal(memberId, MemberRole.USER)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memberId는 양수여야 합니다.");
    }

    @Test
    void rejectsNullMemberId() {
        assertThatThrownBy(
                () -> new SecurityPrincipal(null, MemberRole.USER)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("memberId");
    }

    @Test
    void rejectsNullRole() {
        assertThatThrownBy(
                () -> new SecurityPrincipal(25L, null)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("role");
    }

    @Test
    void usesMemberIdAndRoleForValueEquality() {
        SecurityPrincipal first =
                new SecurityPrincipal(25L, MemberRole.USER);
        SecurityPrincipal second =
                new SecurityPrincipal(25L, MemberRole.USER);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}