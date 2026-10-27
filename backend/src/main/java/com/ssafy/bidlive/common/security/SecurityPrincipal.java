package com.ssafy.bidlive.common.security;

import com.ssafy.bidlive.member.domain.MemberRole;

import java.security.Principal;
import java.util.Objects;

public record SecurityPrincipal(
        Long memberId,
        MemberRole role
) implements Principal {

    public SecurityPrincipal {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(role, "role");

        if (memberId <= 0) {
            throw new IllegalArgumentException(
                    "memberId는 양수여야 합니다."
            );
        }
    }

    @Override
    public String getName() {
        return memberId.toString();
    }
}