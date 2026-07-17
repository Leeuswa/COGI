/*
 * 토스 결제창(빌링 등록) 성공 콜백.
 * 토스가 successUrl(?planId=...)로 리다이렉트하며 customerKey/authKey를 붙여준다.
 * 여기서 결제수단 등록 → 구독 시작 → 약관 이력까지 처리한 뒤 요금제로 돌아간다.
 */
import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";

export default function BillingSuccess() {
  const [params] = useSearchParams();
  const nav = useNavigate();
  const { patchUser } = useAuth();
  const ran = useRef(false); // StrictMode 이중 실행 방지 — 구독이 2번 생성되지 않게
  const [msg, setMsg] = useState("결제수단을 등록하는 중…");

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    const authKey = params.get("authKey");
    const customerKey = params.get("customerKey");
    const planId = Number(params.get("planId"));

    if (!authKey || !customerKey || !planId) {
      setMsg("결제 정보가 올바르지 않아요. 요금제에서 다시 시도해주세요.");
      return;
    }

    (async () => {
      try {
        // 결제(PAYMENT) 필수 약관 id는 DB마다 다르므로 하드코딩하지 않고 조회해서 넘긴다
        const terms = await api.getTerms();
        const paymentTermIds = terms.filter((t) => t.type === "PAYMENT").map((t) => t.id);

        const pmId = await api.registerPaymentMethod({ authKey, customerKey }); // authKey → 빌링키 발급·저장
        await api.startSubscription(planId, pmId, paymentTermIds); // createSubscription이 동의 이력까지 저장
        const fresh = await api.getMyPlan(); // 캐시 동기화용
        patchUser({ planName: fresh.planName });
        nav("/app/plan", { replace: true });
      } catch (e) {
        // 백엔드가 실어보낸 실제 메시지를 그대로 노출(진단용)
        setMsg(`결제 처리에 실패했어요: ${e?.message || "알 수 없는 오류"}`);
      }
    })();
  }, []);

  return (
    <main className="app-main">
      <div className="panel">{msg}</div>
    </main>
  );
}
