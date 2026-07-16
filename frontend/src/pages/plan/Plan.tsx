/*
 * 요금제 · 구독 (SUB-001, PAY-001~002, API-057/059/060/061/062)
 * - 플랜 카드 3장 (랜딩 요금제 섹션과 같은 데이터, 다른 레이아웃)
 * - 결제는 "테스트 모드" (FR-86): 카드번호를 넣는 척만 하고 실결제 없음. 화면에 명시.
 * - 플랜 전환은 같은 구독 건에서 plan_id만 교체 (FR-87) + 차액 안내 (FR-88)
 * - 하단에 subscription_history 테이블.
 */
import { useEffect, useState } from "react";
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
  const [cardNo, setCardNo] = useState("");
  const [busy, setBusy] = useState(false);

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
        await api.cancelSubscription(1);
        notify("구독을 해지했어요. FREE로 전환됩니다");
      } else if (user.planName === "FREE") {
        const pm = await api.registerPaymentMethod({ cardNo }); // API-059
        await api.startSubscription(paying.id, pm.paymentMethodId, [4]); // API-060 (약관 id 4 = 결제/구독)
        await api.submitAgreements([{ termId: 4, agreed: true }]); // 동의 이력 남김 (FR-89)
        notify(`${paying.name} 구독 시작! (테스트 결제 — 실청구 없음)`);
      } else {
        const res = await api.changePlan(1, paying.id); // API-061
        notify(
          `플랜 전환 완료. 차액 ${res.proratedAmount?.toLocaleString() ?? 0}원 (테스트 계산)`,
        );
      }
      patchUser({ planName: paying.name });
      // 헤더 문구·현재 카드 표시가 같은 원천을 보도록 mine 상태도 즉시 동기화
      setMine((m) => ({
        ...m,
        planName: paying.name,
        dailyCreditLimit: paying.dailyCreditLimit,
      }));
      setHistory((h) => [
        {
          id: h.length + 1,
          previousPlan: user.planName,
          newPlan: paying.name,
          changeType:
            paying.name === "FREE"
              ? "CANCEL"
              : user.planName === "FREE"
                ? "START"
                : "CHANGE",
          proratedAmount: 0,
          changedAt: new Date().toISOString(),
        },
        ...h,
      ]);
      setPaying(null);
      setCardNo("");
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

      <div className="panel-grid c3">
        {plans.map((p) => {
          const isCurrent = user.planName === p.name;
          return (
            <div
              key={p.id}
              className={`panel plan-card ${isCurrent ? "current" : ""}`}
            >
              {isCurrent && <div className="plan-flag">현재 이용 중</div>}
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
                {p.model} · 일 {p.dailyCreditLimit}크레딧
              </p>
              <ul
                style={{
                  listStyle: "none",
                  fontSize: 12.5,
                  lineHeight: 2,
                  marginBottom: 16,
                }}
              >
                {p.features.map((ft) => (
                  <li key={ft}>• {ft}</li>
                ))}
              </ul>
              <button
                className={`btn ${isCurrent ? "wh" : "co"} sm`}
                style={{ width: "100%" }}
                disabled={isCurrent}
                onClick={() => setPaying(p)}
              >
                {isCurrent
                  ? "사용 중"
                  : p.name === "FREE"
                    ? "해지하고 FREE로"
                    : "이 플랜으로"}
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
            실제 결제가 일어나지 않는 테스트 환경입니다. 아무 숫자나 넣어도
            통과돼요. (FR-86)
          </p>
          {paying.name !== "FREE" && user.planName === "FREE" && (
            <div className="form" style={{ marginBottom: 14 }}>
              <div>
                <label>카드번호 (테스트)</label>
                <input
                  type="text"
                  inputMode="numeric"
                  placeholder="0000-0000-0000-0000"
                  value={cardNo}
                  onChange={(e) => setCardNo(e.target.value)}
                />
              </div>
            </div>
          )}
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
