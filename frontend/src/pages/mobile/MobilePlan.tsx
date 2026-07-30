/*
 * 모바일 전용 요금제.
 * 데스크톱은 3열 카드 + 5열 이력 표. 폰에서는 카드가 세로로 쌓이며 뱃지가 겹치고 표는 잘린다.
 * 카드를 폰 폭에 맞춰 다시 짜고 이력 표는 카드 리스트로 바꾼다.
 * 기능은 데스크톱과 같다 — 플랜 전환·해지·해지 취소·토스 카드 등록·약관 동의·변경 이력.
 */
import { useEffect, useState } from "react";
import { loadTossPayments } from "@tosspayments/tosspayments-sdk";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import "../../styles/mobile/plan.css";

// 모델 ID에서 버전 토큰을 떼고 표시용 이름으로. "gpt-5.6-luna" → "GPT Luna"
// (데스크톱 Plan.tsx에도 같은 함수가 있다. 남의 파일을 건드리지 않으려고 여기 따로 뒀다)
const fmtModels = (csv) =>
  (csv ?? "")
    .split(",")
    .filter(Boolean)
    .map((id) =>
      id
        .split("-")
        .filter((seg) => !/^\d/.test(seg))
        .map((seg) => (seg === "gpt" ? "GPT" : seg.charAt(0).toUpperCase() + seg.slice(1)))
        .join(" "),
    )
    .join(", ");

const CHANGE_KO = { START: "시작", CHANGE: "전환", CANCEL: "해지" };

export default function MobilePlan() {
  const { user, patchUser } = useAuth();
  const { notify } = useGame();

  const [plans, setPlans] = useState([]);
  const [mine, setMine] = useState(null);
  const [history, setHistory] = useState([]);
  const [paying, setPaying] = useState(null);
  const [payAgree, setPayAgree] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.getPlans().then(setPlans);
    api.getMyPlan().then(setMine);
    api.getSubHistory().then(setHistory);
  }, []);

  // 신규 구독 — 카드 정보는 토스 결제창에서만 받는다. 성공하면 BillingSuccess가 이어받는다
  const startBillingAuth = async (planId) => {
    const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
    if (!clientKey) { notify("결제 설정(clientKey)이 없습니다"); return; }
    // crypto.randomUUID는 secure context(HTTPS/localhost)에만 있다 — HTTP로 띄운 IP 접속에선 없어서 폴백을 둔다.
    const customerKey = crypto.randomUUID?.() ?? `k${Date.now()}${Math.random().toString(36).slice(2, 10)}`;
    const tossPayments = await loadTossPayments(clientKey);
    const payment = tossPayments.payment({ customerKey });
    await payment.requestBillingAuth({
      method: "CARD",
      successUrl: `${window.location.origin}/app/billing/success?planId=${planId}`,
      failUrl: `${window.location.origin}/app/billing/fail`,
    });
  };

  const confirm = async () => {
    if (busy) return;
    setBusy(true);
    let changeRes = null;
    try {
      if (paying.name === "FREE") {
        if (!mine?.subscriptionId) {
          notify("이미 FREE 플랜이에요");
          setPaying(null);
          return;
        }
        await api.cancelSubscription(mine.subscriptionId);
        // 즉시 강등하지 않는다 — 만료일에 스케줄러가 FREE로 내린다
        notify("해지 예약됐어요. 만료일까지 현재 플랜을 계속 이용할 수 있어요.");
        const fresh = await api.getMyPlan();
        setMine((m) => ({ ...m, ...fresh }));
        setPaying(null);
        return;
      } else if (!mine?.subscriptionId) {
        await startBillingAuth(paying.id);
        return; // 토스로 페이지가 넘어가므로 아래는 실행되지 않는다
      } else {
        changeRes = await api.changePlan(mine.subscriptionId, paying.id);
        notify(`플랜 전환 완료. 차액 ${changeRes.proratedAmount?.toLocaleString() ?? 0}원 (테스트 계산)`);
      }
      patchUser({ planName: paying.name });
      const fresh = await api.getMyPlan();
      setMine((m) => ({ ...m, ...fresh, dailyCreditLimit: paying.dailyCreditLimit }));
      setHistory((h) => [
        {
          id: h.length + 1,
          previousPlan: user.planName,
          newPlan: paying.name,
          changeType: paying.name === "FREE" ? "CANCEL" : !mine?.subscriptionId ? "START" : "CHANGE",
          proratedAmount: changeRes?.proratedAmount ?? 0,
          changedAt: new Date().toISOString(),
        },
        ...h,
      ]);
      setPaying(null);
    } finally {
      setBusy(false);
    }
  };

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

  const myPlanName = mine?.planName ?? user.planName;
  const myCredit = mine?.dailyCreditLimit ?? plans.find((p) => p.name === myPlanName)?.dailyCreditLimit ?? 20;

  return (
    <main className="mapp">
      <p className="mlead">
        지금은 {myPlanName} 플랜이고, 하루 크레딧은 {myCredit}개예요.<br />
        결제는 테스트 모드라 실제로 청구되지 않습니다.
      </p>

      {mine?.cancelledAt && (
        <section className="mcard mpl-notice">
          해지 예약됨 — <b>{mine.expiresAt}</b>까지 {mine.planName} 플랜을 계속 쓸 수 있어요.
          그 뒤 자동으로 FREE로 바뀝니다.
        </section>
      )}

      {plans.map((p) => {
        const currentName = mine?.planName ?? user.planName;
        const currentPrice = plans.find((x) => x.name === currentName)?.price ?? 0;
        const isCancelled = !!mine?.cancelledAt;
        const isReservedPlan = isCancelled && p.name === currentName;
        const isFreeSelected = isCancelled && p.name === "FREE";
        const isCurrent = !isCancelled && currentName === p.name;
        // 정책: 다운그레이드 없음 (업그레이드 + 해지만)
        const isDowngrade = !isCancelled && p.name !== "FREE" && p.price < currentPrice;

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
          <section key={p.id} className={`mcard mpl ${isCurrent || isFreeSelected ? "on" : ""}`}>
            <div className="mpl-top">
              <b>{p.name}</b>
              {isCurrent && <span className="mpl-flag">현재 이용 중</span>}
              {isFreeSelected && <span className="mpl-flag">전환 예정</span>}
              {isReservedPlan && <span className="mpl-flag">해지 예약됨</span>}
            </div>
            <p className="mpl-price">
              {p.price === 0 ? "₩0" : `₩${p.price.toLocaleString()}`}<i>/월</i>
            </p>
            {/* 모델을 한 문장으로 붙이면 오른쪽 정렬로 접혀 읽기 나쁘다. 알약으로 늘어놓는다 */}
            <p className="mpl-label">사용 가능 모델</p>
            <div className="mpl-models">
              {fmtModels(p.allowedModels).split(", ").filter(Boolean).map((m) => (
                <span key={m}>{m}</span>
              ))}
            </div>
            <p className="mpl-credit">하루 크레딧 <b>{p.dailyCreditLimit}개</b></p>
            <ul className="mpl-feat">
              {(p.features ?? []).map((ft) => <li key={ft}>{ft}</li>)}
            </ul>
            <button
              className={`btn ${btnDisabled ? "wh" : "co"} sm full`}
              disabled={btnDisabled || busy}
              onClick={btnOnClick}
            >
              {btnLabel}
            </button>
          </section>
        );
      })}

      {/* 결제 확정 — 데스크톱과 같이 모달 대신 인라인 패널 */}
      {paying && (
        <section className="mcard mpl-pay">
          <div className="mcard-head">
            <h2>💳 {paying.name} {paying.name === "FREE" ? "전환(해지)" : "결제"} — 테스트 모드</h2>
          </div>
          <p className="mnote">
            {!mine?.subscriptionId && paying.name !== "FREE"
              ? "약관에 동의하고 확정하면 토스 결제창에서 카드를 등록합니다. (테스트 모드 — 실청구 없음)"
              : mine?.subscriptionId && paying.name !== "FREE"
                ? "등록된 카드로 남은 기간만큼의 차액이 일할계산되어 즉시 청구됩니다. (테스트 모드 — 실청구 없음)"
                : "테스트 모드라 실제로 청구되지 않습니다."}
          </p>
          {paying.name !== "FREE" && (
            <label className="mpl-agree">
              <input type="checkbox" checked={payAgree} onChange={(e) => setPayAgree(e.target.checked)} />
              [필수] 결제/구독 이용약관 (v1.0)에 동의합니다
            </label>
          )}
          <button
            className="btn co sm full"
            onClick={confirm}
            disabled={busy || (paying.name !== "FREE" && !payAgree)}
          >
            {busy ? "처리 중…" : "확정"}
          </button>
          <button
            className="btn wh sm full mpl-cancel"
            onClick={() => { setPaying(null); setPayAgree(false); }}
          >
            취소
          </button>
        </section>
      )}

      {/* 구독 변경 이력 — 5열 표는 폰에서 못 읽는다 */}
      {history.length > 0 && (
        <section className="mcard">
          <div className="mcard-head"><h2>구독 변경 이력</h2></div>
          <ul className="mpl-hist">
            {history.map((h) => (
              <li key={h.id}>
                <span className={`mpl-tag ${h.changeType === "CANCEL" ? "off" : ""}`}>
                  {CHANGE_KO[h.changeType] ?? h.changeType}
                </span>
                <span className="mpl-hbody">
                  <b>{h.previousPlan} → {h.newPlan}</b>
                  <i>{h.changedAt.slice(0, 16).replace("T", " ")}</i>
                </span>
                <span className="mpl-amt">{h.proratedAmount?.toLocaleString() ?? 0}원</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  );
}
