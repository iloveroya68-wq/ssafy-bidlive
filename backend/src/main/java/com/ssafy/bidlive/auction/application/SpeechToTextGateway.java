package com.ssafy.bidlive.auction.application;

/** 판매자 음성 바이너리를 확정 한국어 문장으로 변환하는 외부 STT 포트다. */
public interface SpeechToTextGateway {

	String transcribe(String contentType, byte[] audio);
}
