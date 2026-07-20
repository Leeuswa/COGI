/*
 * 요금제 · 구독 (SUB-001, PAY-001~002, API-057/059/060/061/062)
 * - 플랜 카드 3장 (랜딩 요금제 섹션과 같은 데이터, 다른 레이아웃)
 * - 결제는 "테스트 모드" (FR-86): 카드번호를 넣는 척만 하고 실결제 없음. 화면에 명시.
 * - 플랜 전환은 같은 구독 건에서 plan_id만 교체 (FR-87) + 차액 안내 (FR-88)
 * - 하단에 subscription_history 테이블.
 */
import { useEffect, useState } from "react";
import { loadTossPayments } from "@tosspayments/tosspayments-sdk";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import { PageHead } from "../../components/ui";

export default function Plan() {
  const { user, patchUser } = useAuth();
  const { notify } = useGame();

  const [plans, setPlans] = useState([]);
  const [mine, setMine] = useState(null); // 내 현재 플랜/한도 (API-058, 원천은 subscriptions)
  const [history, setHistory] = useState([]);
  const [paying, setPaying] = useState(null); // 결제 모달 대상 플랜
  const [payAgree, setPayAgree] = useState(false); // 결제/구독 약관 동의 (결제 시 필수, PAY-001)
  const [busy, setBusy] = useState(false);

  // 신규 구독 — 토스 결제창(카드 등록)으로 리다이렉트. 카드정보는 토스 창에서만 입력받는다.
  // 성공 시 successUrl(?planId=...)로 돌아오며 토스가 customerKey/authKey를 붙여준다 → BillingSuccess가 처리.
  const startBillingAuth = async (planId) => {
    const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
    if (!clientKey) { notify("결제 설정(clientKey)이 없습니다"); return; }
    const customerKey = crypto.randomUUID(); // 결제창에 넘긴 값이 발급 요청과 동일해야 함(토스가 콜백에 되돌려줌)
    const tossPayments = await loadTossPayments(clientKey);
    const payment = tossPayments.payment({ customerKey });
    await payment.requestBillingAuth({
      method: "CARD",
      successUrl: `${window.location.origin}/app/billing/success?planId=${planId}`,
      failUrl: `${window.location.origin}/app/billing/fail`,
    });
  };

  useEffect(() => {
    api.getPlans().then(setPlans);
    api.getMyPlan().then(setMine);
    api.getSubHistory().then(setHistory);
  }, []);

  // 결제 확정. FREE로 내려가는 건 해지(DELETE), 나머지는 시작/전환
  const confirm = async () => {
    if (busy) return;
    setBusy(true);
    try {
      if (paying.name === "FREE") {
        // 구독 행이 없으면(이미 FREE) 해지할 게 없음 — 잘못된 /subscriptions/undefined 호출 방지
        if (!mine?.subscriptionId) {
          notify("이미 FREE 플랜이에요");
          setPaying(null);
          return;
        }
        await api.cancelSubscription(mine.subscriptionId);
        // 즉시 FREE로 바꾸지 않는다 — 해지 예약만. 만료일(expires_at)에 스케줄러가 FREE로 강등.
        notify("해지 예약됐어요. 만료일까지 현재 플랜을 계속 이용할 수 있어요.");
        const fresh = await api.getMyPlan(); // cancelledAt/expiresAt 반영 → "해지 예약됨" 배너 즉시 표시
        setMine((m) => ({ ...m, ...fresh }));
        setPaying(null);
        return;
      } else if (!mine?.subscriptionId) {
        // 활성 구독 없음 = 신규 구독 → 토스 결제창으로 이동. 카드 등록·구독 생성은 successUrl(BillingSuccess)에서.
        await startBillingAuth(paying.id);
        return; // 페이지가 토스로 리다이렉트되므로 이후 로직은 실행되지 않음
      } else {
        // 활성 구독 있음 = 전환 (subscriptionId 보장됨)
        const res = await api.changePlan(mine.subscriptionId, paying.id); // API-061
        notify(
          `플랜 전환 완료. 차액 ${res.proratedAmount?.toLocaleString() ?? 0}원 (테스트 계산)`,
        );
      }
      patchUser({ planName: paying.name });
      // 성공 후 서버에서 최신 구독 상태 재조회 — subscriptionId까지 갱신돼 다음 액션이 stale값을 안 씀.
      // MyPlanResponseDTO엔 dailyCreditLimit가 없어 방금 고른 플랜값으로 보존.
      const fresh = await api.getMyPlan();
      setMine((m) => ({ ...m, ...fresh, dailyCreditLimit: paying.dailyCreditLimit }));
      setHistory((h) => [
        {
          id: h.length + 1,
          previousPlan: user.planName,
          newPlan: paying.name,
          changeType:
            paying.name === "FREE"
              ? "CANCEL"
              : !mine?.subscriptionId
                ? "START"
                : "CHANGE",
          proratedAmount: 0,
          changedAt: new Date().toISOString(),
        },
        ...h,
      ]);
      setPaying(null);
    } finally {
      setBusy(false);
    }
  };

  // 해지 예약 취소 — 현재 보유 플랜을 계속 유지
  const resume = async () => {
    if (busy || !mine?.subscriptionId) return;
    setBusy(true);
    try {
      await api.resumeSubscription(mine.subscriptionId);
      notify("해지를 취소했어요. 구독이 계속 유지됩니다.");
      const fresh = await api.getMyPlan();
      setMine((m) => ({ ...m, ...fresh }));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="app-main">
      <PageHead
        badge="PLAN"
        title="요금제"
        lead={`지금은 ${mine?.planName ?? user.planName} 플랜이고, 하루 크레딧은 ${mine?.dailyCreditLimit ?? 20}개예요.\n결제는 테스트 모드라 실제로 청구되지 않습니다.`}
      />

      {/* 해지 예약 상태 — 만료일까지 현재 플랜 유지, 그 날 FREE로 강등 */}
      {mine?.cancelledAt && (
        <div className="panel" style={{ outline: "2px solid var(--sub)", marginBottom: 14 }}>
          해지 예약됨 — <b>{mine.expiresAt}</b>까지 {mine.planName} 플랜을 계속 이용할 수 있어요.
          그 이후 자동으로 FREE로 전환됩니다.
        </div>
      )}

      <div className="panel-grid c3">
        {plans.map((p) => {
          // 현재 플랜은 서버 실제값(getMyPlan) 기준. user.planName(localStorage 캐시)은 로딩 전 폴백만.
          const currentName = mine?.planName ?? user.planName;
          const currentPrice = plans.find((x) => x.name === currentName)?.price ?? 0;
          const isCancelled = !!mine?.cancelledAt; // 해지 예약 상태

          // 해지 예약 상태: FREE는 선택된 듯, 현재 보유 플랜은 "해지 취소하기", 나머지는 비활성
          const isReservedPlan = isCancelled && p.name === currentName; // 만료까지 유지되는 현재 유료 플랜
          const isFreeSelected = isCancelled && p.name === "FREE"; // 전환될 FREE (선택 표시)

          const isCurrent = !isCancelled && currentName === p.name;
          // 정책: 다운그레이드 없음(업그레이드+해지만). 유료인데 현재보다 저렴하면 전환 불가.
          const isDowngrade = !isCancelled && p.name !== "FREE" && p.price < currentPrice;
          const highlighted = isCurrent || isFreeSelected; // 선택/현재 강조 스타일

          // 버튼 상태 결정
          let btnLabel, btnDisabled = false, btnOnClick = () => setPaying(p);
          if (isCancelled) {
            if (isReservedPlan) { btnLabel = "해지 취소하기"; btnOnClick = resume; }
            else if (isFreeSelected) { btnLabel = "전환 예정"; btnDisabled = true; }
            else { btnLabel = "선택 불가"; btnDisabled = true; }
          } else if (isCurrent) { btnLabel = "사용 중"; btnDisabled = true; }
          else if (isDowngrade) { btnLabel = "하위 요금제입니다"; btnDisabled = true; }
          else if (p.name === "FREE") { btnLabel = "해지하고 FREE로"; }
          else { btnLabel = "이 플랜으로"; }
          return (
            <div
              key={p.id}
              className={`panel plan-card ${highlighted ? "current" : ""}`}
            >
              {isCurrent && <div className="plan-flag">현재 이용 중</div>}
              {isFreeSelected && <div className="plan-flag">전환 예정</div>}
              {isReservedPlan && <div className="plan-flag">해지 예약됨</div>}
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <b
                  style={{ fontFamily: "Silkscreen, monospace", fontSize: 16 }}
                >
                  {p.name}
                </b>
              </div>
              <p style={{ fontSize: 22, margin: "10px 0 2px" }}>
                {p.price === 0 ? "₩0" : `₩${p.price.toLocaleString()}`}
                <span className="note xs">/월</span>
              </p>
              <p
                style={{
                  fontSize: 12.5,
                  color: "var(--sub)",
                  marginBottom: 12,
                }}
              >
                {p.allowedModels} · 일 {p.dailyCreditLimit}크레딧
              </p>
              <ul
                style={{
                  listStyle: "none",
                  fontSize: 12.5,
                  lineHeight: 2,
                  marginBottom: 16,
                }}
              >
                {(p.features ?? []).map((ft) => (
                  <li key={ft}>• {ft}</li>
                ))}
              </ul>
              <button
                className={`btn ${btnDisabled ? "wh" : "co"} sm`}
                style={{ width: "100%" }}
                disabled={btnDisabled || busy}
                onClick={btnOnClick}
              >
                {btnLabel}
              </button>
            </div>
          );
        })}
      </div>

      {/* 테스트 결제 패널 — 모달 대신 인라인 (픽셀 UI엔 이게 더 어울림) */}
      {paying && (
        <div className="panel" style={{ outline: "3px solid var(--coral)" }}>
          <h3>
            💳 {paying.name} {paying.name === "FREE" ? "전환(해지)" : "결제"} —
            테스트 모드
          </h3>
          <p className="note" style={{ marginBottom: 14 }}>
            {!mine?.subscriptionId && paying.name !== "FREE"
              ? "약관 동의 후 확정하면 토스 결제창에서 카드를 등록합니다. (테스트 모드 — 실청구 없음)"
              : "테스트 모드라 실제로 청구되지 않습니다."}
          </p>
          {paying.name !== "FREE" && (
            <label className="check-row" style={{ marginBottom: 14 }}>
              <input
                type="checkbox"
                checked={payAgree}
                onChange={(e) => setPayAgree(e.target.checked)}
              />
              [필수] 결제/구독 이용약관 (v1.0)에 동의합니다
            </label>
          )}
          <div style={{ display: "flex", gap: 10 }}>
            <button
              className="btn co sm"
              onClick={confirm}
              disabled={busy || (paying.name !== "FREE" && !payAgree)}
            >
              {busy ? "처리 중…" : "확정"}
            </button>
            <button
              className="btn wh sm"
              onClick={() => {
                setPaying(null);
                setPayAgree(false);
              }}
            >
              취소
            </button>
          </div>
        </div>
      )}

      {/* 구독 변경 이력 (subscription_history) */}
      <div className="panel flush">
        <table className="tbl">
          <thead>
            <tr>
              <th>변경</th>
              <th>이전</th>
              <th>이후</th>
              <th>차액</th>
              <th>일시</th>
            </tr>
          </thead>
          <tbody>
            {history.map((h) => (
              <tr key={h.id}>
                <td>
                  <span
                    className={`chip ${h.changeType === "CANCEL" ? "gray" : "navy"}`}
                  >
                    {h.changeType}
                  </span>
                </td>
                <td className="mono">{h.previousPlan}</td>
                <td className="mono">{h.newPlan}</td>
                <td className="mono">
                  {h.proratedAmount?.toLocaleString() ?? 0}원
                </td>
                <td className="mono xs">
                  {h.changedAt.slice(0, 16).replace("T", " ")}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </main>
  );
}
