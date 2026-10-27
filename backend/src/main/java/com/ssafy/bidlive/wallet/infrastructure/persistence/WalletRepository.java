package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.util.List;

import com.ssafy.bidlive.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

	List<Wallet> findAllByMemberId(Long memberId);
}
