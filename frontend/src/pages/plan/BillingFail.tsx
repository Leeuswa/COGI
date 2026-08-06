/*
 * 토스 결제창 취소/실패 콜백 (failUrl).
 * 토스가 code/message 쿼리를 붙여 보낸다.
 */
import { Link, useSearchParams } from "react-router-dom";

export default function BillingFail() {
  const [params] = useSearchParams();
  return (
    <main className="app-main">
      <div className="panel">
        <h3>결제수단 등록이 취소되었어요</h3>
        <p className="note" style={{ marginBottom: 14 }}>
          {params.get("message") || "다시 시도해주세요."}
        </p>
        <Link className="btn co sm" to="/app/plan">
          요금제로 돌아가기
        </Link>
      </div>
    </main>
  );
}
