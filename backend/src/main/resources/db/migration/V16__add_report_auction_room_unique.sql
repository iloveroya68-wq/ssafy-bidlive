-- DEAL-04 경매 신고 중복 방지.
--
-- 앱에서 EXISTS로 먼저 걸러도 그 검사와 INSERT 사이는 비어 있다. READ COMMITTED에서 두 번째 요청은
-- 첫 요청의 미커밋 행을 보지 못해 둘 다 통과하고, 커밋 뒤에도 이미 통과한 쪽은 그대로 INSERT한다.
-- 트랜잭션만으로는 막히지 않으므로 보장은 DB가 진다(원장 idempotency_key와 같은 방식).
--
-- 부분 인덱스인 이유: `report`는 회원·경매방·채팅·거래 신고를 한 테이블에 담고
-- `ck_report_target_count`가 대상 컬럼 하나만 채우게 한다. 경매방 신고가 아닌 행은
-- `auction_room_id`가 NULL이라 이 제약과 무관해야 한다.
CREATE UNIQUE INDEX uq_report_auction_room_reporter
    ON report (auction_room_id, reporter_member_id)
    WHERE auction_room_id IS NOT NULL;
