/*
 * 모바일 전용 마이페이지.
 * 데스크톱은 탭 5개가 한 줄인데 폰에서는 줄바꿈되며 어디가 눌린 건지 안 보인다.
 * 탭을 3×2 세그먼트로 접고, 약관은 4열 표 대신 줄 리스트로 바꾼다.
 * 기능은 데스크톱과 같다 — 프로필 저장·비밀번호 변경·GitHub 연동·TOTP 설정/활성화·약관 현황·공지.
 */
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import * as api from "../../api/client";
import { isValidPassword, PW_HINT } from "../../utils/password";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import InterestPicker from "../../components/InterestPicker";
import { TermsDialog } from "../../components/ui";
import "../../styles/mobile/my.css";

const LEVELS = [["BEGINNER", "초급"], ["INTERMEDIATE", "중급"], ["ADVANCED", "고급"]];
// 라벨이 길면 세그먼트가 두 줄로 접히니 짧게 (GitHub 연동 → GitHub)
const TABS: [string, string][] = [
  ["profile", "프로필"], ["github", "GitHub"], ["security", "보안"],
  ["terms", "약관"], ["notice", "공지"],
];
const fmtDate = (d) => (d ? d.slice(0, 10).replace(/-/g, ".") : "");

export default function MobileMy() {
  const { user, patchUser } = useAuth();
  const { notify } = useGame();

  const [tab, setTab] = useState("profile");
  const [name, setName] = useState(user.name || "");
  const [level, setLevel] = useState(user.level || "BEGINNER");
  const [interests, setInterests] = useState(user.interests || []);
  const [totp, setTotp] = useState(null);
  const [code, setCode] = useState("");
  const [terms, setTerms] = useState([]);
  const [agreedIds, setAgreedIds] = useState([]);
  const [viewTerm, setViewTerm] = useState(null);
  const [notices, setNotices] = useState(null);
  const [openNotice, setOpenNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const [pwOpen, setPwOpen] = useState(false);
  const [pw, setPw] = useState({ cur: "", next: "", confirm: "" });
  const [pwBusy, setPwBusy] = useState(false);
  const [linkError, setLinkError] = useState("");

  // 소셜 가입자는 닉네임·이메일이 제공자 소유라 여기서 못 바꾼다
  const isSocial = user.provider === "KAKAO" || user.provider === "GITHUB";
  const providerName = user.provider === "KAKAO" ? "카카오" : user.provider === "GITHUB" ? "GitHub" : null;

  useEffect(() => {
    api.getTerms().then(setTerms);
    api.getMyAgreements().then(setAgreedIds).catch(() => setAgreedIds([]));
  }, []);

  // 공지는 탭을 처음 열 때만 받는다
  useEffect(() => {
    if (tab === "notice" && notices === null) api.getNotices().then(setNotices).catch(() => setNotices([]));
  }, [tab, notices]);

  // GitHub 연동 리다이렉트 결과 — 백엔드 콜백이 ?linked=1 또는 ?error=... 로 되돌려보낸다
  const [params, setParams] = useSearchParams();
  useEffect(() => {
    if (params.get("linked")) {
      notify("GitHub 연동 완료!");
      api.getProfile().then((u) => patchUser({ githubUsername: u.githubUsername }));
      setTab("github");
      setParams({}, { replace: true });
    } else if (params.get("error")) {
      setLinkError(params.get("error") === "github_already_linked"
        ? "이미 연결된 GitHub 계정입니다."
        : "GitHub 연동에 실패했어요. 다시 시도해주세요.");
      setTab("github");
      setParams({}, { replace: true });
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps -- 최초 1회만

  const toggle = (t) =>
    setInterests((prev) => (prev.includes(t) ? prev.filter((x) => x !== t) : [...prev, t]));

  const saveProfile = async () => {
    if (!isSocial && !name.trim()) return notify("닉네임을 입력해주세요");
    if (interests.length === 0) return notify("관심기술은 1개 이상 골라주세요");
    setBusy(true);
    try {
      await api.updateProfile(name.trim(), level, interests);
      patchUser({ name: name.trim(), level, interests });
      notify("프로필이 저장됐어요. 다음 리뷰부터 바로 반영돼요");
    } catch (ex) {
      notify(ex.message || "저장에 실패했어요. 잠시 후 다시 시도해주세요");
    } finally { setBusy(false); }
  };

  const changePw = async () => {
    if (!isValidPassword(pw.next)) return notify(PW_HINT);
    if (pw.next !== pw.confirm) return notify("새 비밀번호가 서로 달라요. 다시 확인해주세요");
    if (pw.next === pw.cur) return notify("기존 비밀번호와 다른 비밀번호를 사용해주세요");
    setPwBusy(true);
    try {
      await api.changePassword(pw.cur, pw.next, pw.confirm);
      notify("비밀번호를 바꿨어요. 다음 로그인부터 적용됩니다");
      setPw({ cur: "", next: "", confirm: "" });
      setPwOpen(false);
    } catch (ex) {
      notify(ex.message || "현재 비밀번호가 맞지 않아요");
    } finally { setPwBusy(false); }
  };

  // 1단계: 시크릿/QR 발급만. 실제 활성화는 코드 검증 후
  const setupTotp = async () => {
    setBusy(true);
    try { setTotp(await api.totpSetup()); }
    catch (ex) { notify(ex.message || "OTP 설정에 실패했어요"); }
    finally { setBusy(false); }
  };

  const enableTotp = async () => {
    setBusy(true);
    try {
      await api.totpEnable(code);
      patchUser({ totpEnabled: true });
      setTotp(null);
      setCode("");
      notify("2차 인증이 켜졌어요. 다음 로그인부터 6자리를 물어봅니다");
    } catch (ex) {
      notify(ex.message || "인증 코드가 올바르지 않아요");
    } finally { setBusy(false); }
  };

  // 2차 인증 해제 — 켜진 사람만. 해제해야 다시 설정 가능
  const disableTotp = async () => {
    setBusy(true);
    try {
      await api.totpDisable();
      patchUser({ totpEnabled: false });
      setTotp(null);
      notify("2차 인증을 해제했어요.");
    } catch (ex) {
      notify(ex.message || "해제에 실패했어요");
    } finally { setBusy(false); }
  };

  const agreed = new Set(agreedIds);

  return (
    <main className="mapp">
      <p className="mlead">{user.name ? `${user.name} · ${user.email}` : user.email}</p>

      <div className="mseg mmy-seg">
        {TABS.map(([k, label]) => (
          <button key={k} className={tab === k ? "on" : ""} onClick={() => setTab(k)}>{label}</button>
        ))}
      </div>

      {/* ── 프로필 ── */}
      {tab === "profile" && (
        <>
          <section className="mcard">
            <div className="mcard-head"><h2>계정 정보</h2></div>
            <label className="mmy-f">
              <span>이름(닉네임){isSocial && ` — ${providerName} 제공, 수정 불가`}</span>
              <input type="text" value={name} maxLength={20} disabled={isSocial}
                onChange={(e) => setName(e.target.value)} />
            </label>
            <label className="mmy-f">
              <span>이메일{isSocial ? ` — ${providerName} 제공, 수정 불가` : " — 로그인 ID라 변경 불가"}</span>
              <input type="email" value={user.email} disabled />
            </label>
            {isSocial && (
              <p className="mnote">
                {providerName} 계정에서 가져온 정보예요.<br />
                바꾸려면 {providerName} 쪽에서 수정하면 다음 로그인 때 따라옵니다.
              </p>
            )}
          </section>

          <section className="mcard">
            <div className="mcard-head"><h2>코딩 수준</h2></div>
            <div className="mseg mmy-lv">
              {LEVELS.map(([codeVal, label]) => (
                <button key={codeVal} className={level === codeVal ? "on" : ""} onClick={() => setLevel(codeVal)}>
                  {label}
                </button>
              ))}
            </div>
          </section>

          <section className="mcard">
            <div className="mcard-head"><h2>관심 기술</h2></div>
            <InterestPicker value={interests} onToggle={toggle} />
          </section>

          {/* 소셜 가입자는 비밀번호가 없다 */}
          {!isSocial && (
            <section className="mcard">
              <div className="mcard-head"><h2>비밀번호 재설정</h2></div>
              {!pwOpen ? (
                <button className="btn wh sm full" onClick={() => setPwOpen(true)}>비밀번호 재설정</button>
              ) : (
                <>
                  <label className="mmy-f">
                    <span>현재 비밀번호</span>
                    <input type="password" value={pw.cur} autoComplete="current-password"
                      onChange={(e) => setPw((v) => ({ ...v, cur: e.target.value }))} />
                  </label>
                  <label className="mmy-f">
                    <span>새 비밀번호 (영어·숫자·특수문자 포함 8자 이상)</span>
                    <input type="password" value={pw.next} autoComplete="new-password"
                      onChange={(e) => setPw((v) => ({ ...v, next: e.target.value }))} />
                  </label>
                  <label className="mmy-f">
                    <span>새 비밀번호 확인</span>
                    <input type="password" value={pw.confirm} autoComplete="new-password"
                      onChange={(e) => setPw((v) => ({ ...v, confirm: e.target.value }))} />
                  </label>
                  <button className="btn co sm full" onClick={changePw} disabled={pwBusy}>
                    {pwBusy ? "변경 중…" : "변경하기"}
                  </button>
                  <button className="btn wh sm full mmy-second" onClick={() => setPwOpen(false)}>취소</button>
                </>
              )}
            </section>
          )}

          <button className="btn co full mmy-save" onClick={saveProfile} disabled={busy}>
            {busy ? "저장 중…" : "프로필 저장"}
          </button>
        </>
      )}

      {/* ── GitHub 연동 ── */}
      {tab === "github" && (
        <section className="mcard">
          <div className="mcard-head"><h2>GitHub 계정 연동</h2></div>
          {user.githubUsername ? (
            <p className="mnote">
              <span className="mmy-ok">LINKED</span> @{user.githubUsername} 로 연동되어 있어요.<br />
              레포 연동과 PR 자동 리뷰를 쓸 수 있습니다.
            </p>
          ) : (
            <>
              <p className="mnote">
                GitHub를 연동하면 레포 목록을 불러오고 PR마다 자동 리뷰가 돌아갑니다.<br />
                단, 이미 다른 계정에 연결된 GitHub은 연동할 수 없어요.
              </p>
              {linkError && <p className="mmy-err">{linkError}</p>}
              {/* 백엔드 authorize로 풀페이지 이동 → OAuth 시작 */}
              <button className="btn co sm full mmy-second"
                onClick={() => { window.location.href = "/api/users/me/github/authorize"; }} disabled={busy}>
                {busy ? "연동 중…" : "GitHub 연동하기"}
              </button>
            </>
          )}
        </section>
      )}

      {/* ── 보안(OTP) ── */}
      {tab === "security" && (
        <section className="mcard">
          <div className="mcard-head"><h2>TOTP 2차 인증</h2></div>
          <p className="mnote">이메일 로그인에만 적용돼요.<br />GitHub·카카오 로그인은 소셜 쪽 보안을 따릅니다.</p>

          {isSocial ? (
            <>
              <button className="btn co sm full mmy-second" disabled>OTP 설정하기</button>
              <p className="mnote mmy-second">
                {providerName} 로그인 계정은 앱 자체 2단계 인증 대신
                <b> {providerName} 계정의 2단계 인증</b>을 이용하세요.
              </p>
            </>
          ) : user.totpEnabled ? (
            <>
              <p className="mnote mmy-second"><span className="mmy-ok">활성</span> 2차 인증이 <b>완료</b>됐어요.</p>
              <button className="btn wh sm full mmy-second" onClick={disableTotp} disabled={busy}>
                {busy ? "해제 중…" : "2차 인증 해제"}
              </button>
            </>
          ) : totp ? (
            <>
              <p className="mnote mmy-second">인증 앱(Google Authenticator 등)에 등록하세요.</p>
              {totp.qrCodeUrl && <img className="mmy-qr" src={totp.qrCodeUrl} alt="OTP QR" width={168} height={168} />}
              <p className="mnote">QR을 못 읽으면 아래 시크릿을 수동 입력:</p>
              <pre className="codebox mmy-secret">{totp.secret}</pre>
              <input className="msel mmy-code" value={code} inputMode="numeric" maxLength={6}
                placeholder="앱에 뜬 6자리"
                onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))} />
              <button className="btn co sm full mmy-second" onClick={enableTotp} disabled={busy || code.length !== 6}>
                {busy ? "확인 중…" : "활성화"}
              </button>
            </>
          ) : (
            <button className="btn co sm full mmy-second" onClick={setupTotp} disabled={busy}>
              {busy ? "설정 중…" : "OTP 설정하기"}
            </button>
          )}
        </section>
      )}

      {/* ── 약관 ── */}
      {tab === "terms" && (
        <section className="mcard">
          <div className="mcard-head"><h2>약관 동의 현황</h2></div>
          <ul className="mmy-terms">
            {terms.map((t) => (
              <li key={t.id}>
                <button type="button" className="mmy-tname" onClick={() => setViewTerm(t)}>
                  {t.title}
                  <i>v{t.version} · {t.isRequired ? "필수" : "선택"}</i>
                </button>
                <span className={`mmy-tst ${agreed.has(t.id) ? "on" : ""}`}>
                  {agreed.has(t.id) ? "동의함" : t.type === "PAYMENT" ? "결제 시 동의" : "미동의"}
                </span>
              </li>
            ))}
          </ul>
          <TermsDialog term={viewTerm} onClose={() => setViewTerm(null)} />
        </section>
      )}

      {/* ── 공지사항 ── */}
      {tab === "notice" && (
        notices === null ? (
          <p className="mnote">불러오는 중…</p>
        ) : notices.length === 0 ? (
          <section className="mcard mempty"><p>아직 공지가 없어요.</p></section>
        ) : (
          <ul className="mmy-notices">
            {notices.map((n) => {
              const on = openNotice === n.id;
              return (
                <li key={n.id} className={on ? "on" : ""}>
                  <button type="button" className="mmy-nq" onClick={() => setOpenNotice(on ? null : n.id)}>
                    <span>
                      <b>{n.subject}</b>
                      <i>{fmtDate(n.sentAt)}</i>
                    </span>
                    <em>{on ? "−" : "+"}</em>
                  </button>
                  {on && <div className="mmy-na">{n.content}</div>}
                </li>
              );
            })}
          </ul>
        )
      )}
    </main>
  );
}
