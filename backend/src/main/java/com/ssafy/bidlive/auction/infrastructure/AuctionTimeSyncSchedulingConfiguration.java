package com.ssafy.bidlive.auction.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 경매 TIME_SYNC 주기 작업을 활성화한다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AuctionTimeSyncSchedulingConfiguration {
}
