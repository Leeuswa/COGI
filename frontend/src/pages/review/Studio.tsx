/*
 * 리뷰 스튜디오 (LOC-002~003 + FR-38/47~49 통합 작업대)
 *
 * 한 화면에서 이어지는 흐름:
 *   ① 소스 고르기 — [PR 가져오기](연동 레포 PR → 파일 다중 선택/전체) 또는 [직접 붙여넣기]
 *   ② 모델 고르고 리뷰 시작 — 시작하면 모델이 잠긴다 (대화 맥락이 모델에 묶이므로,
 *      바꾸려면 [새 리뷰]. 잠긴 셀렉트에 안내 문구가 뜬다)
 *   ③ 코기와 채팅 — 이슈 말풍선 → 후속 질문 (리뷰·질문 모두 ⚡1, 남발은 일일 한도가 방어)
 *   ④ 프론트 코드가 있으면 상단 [▶ 미리보기] 토글 — 드래그·속성 편집이 코드로 실시간 반영
 *
 * 백엔드 코드도 리뷰는 전부 되고, 미리보기만 HTML/CSS/JS 로 제한된다 (iframe 한계선).
 * PR 상세(팀장 승인·내보내기)는 별도 페이지 유지 — 거기서 [스튜디오에서 이어가기]로 넘어온다.
 */
import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import { PageHead, SevChip } from "../../components/ui";
import { MODEL_TIERS, PLAN_TIER, catKo } from "../../data/constants";
import PreviewDock from "./PreviewDock";

const isFrontend = (f) => /html|css/i.test(f.language) || f.kind === "frontend";

// AI가 description에 섞어 보내는 마크다운 코드 표기(```블록, `인라인`)만 구분해서 렌더링.
// 마크다운 라이브러리 없이, 정규식으로 코드 부분만 잘라내 코드체로 보여준다.
const renderDescription = (text: string) => {
  const parts: React.ReactNode[] = [];
  const regex = /```[\w-]*\n?([\s\S]*?)```|`([^`\n]+)`/g;
  let lastIndex = 0;
  let match;
  let key = 0;
  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(<span key={key++}>{text.slice(lastIndex, match.index)}</span>);
    }
    if (match[1] !== undefined) {
      parts.push(
        <pre key={key++} className="codebox snippet">
          {match[1].trim()}
        </pre>,
      );
    } else {
      parts.push(
        <code key={key++} className="inline-code">
          {match[2]}
        </code>,
      );
    }
    lastIndex = regex.lastIndex;
  }
  if (lastIndex < text.length) {
    parts.push(<span key={key++}>{text.slice(lastIndex)}</span>);
  }
  return parts;
};

export default function Studio() {
  const { user } = useAuth();
  const { spendCredit, refundCredit, notify, S, creditLimit } = useGame();
  const loc = useLocation();
  const fileRef = useRef(null);
  const threadRef = useRef(null); // 채팅 스레드 컨테이너 — 내부 스크롤 전용
  const myTier = PLAN_TIER[user.planName] ?? 1;
  const modelWeight = (m) => MODEL_TIERS.find((t) => t.name === m)?.tier ?? 1; // 크레딧 차감 가중치 = 모델 tier
  const remainingCredit = creditLimit - S.creditUsed;

  const [msgs, setMsgs] = useState([]);
  const [input, setInput] = useState("");
  const [language, setLanguage] = useState("TypeScript");
  const [model, setModel] = useState(MODEL_TIERS[0].name);
  const [busy, setBusy] = useState(false);
  const [reviewId, setReviewId] = useState(null); // null = 아직 시작 전 (모델 변경 가능)
  const nav = useNavigate();
  const [verdicts, setVerdicts] = useState({}); // #6 이슈별 판정 { [issueId]: 'RESOLVED' | 'IGNORED' }
  const [picker, setPicker] = useState(null); // PR 파일 선택 모달 { files, checked:Set }
  const [previewCode, setPreviewCode] = useState(null); // 미리보기 대상 (프론트 파일)
  const [dockOpen, setDockOpen] = useState(false);
  const actionCost = reviewId === null ? modelWeight(model) : 1; // 리뷰 시작=모델 등급, 후속 질문=1
  const insufficientCredit = remainingCredit < actionCost;

  // 새 말풍선 → 채팅 영역 '안에서만' 아래로. scrollIntoView 는 페이지 전체를 끌고 내려가서 금지
  useEffect(() => {
    const t = threadRef.current;
    if (t) t.scrollTop = t.scrollHeight; // scrollTo 미지원 환경도 안전
  }, [msgs, busy]);

  const auto = useRef(false);
  useEffect(() => {
    if (auto.current) return;
    auto.current = true;
    (async () => {
      if (loc.state?.prId) {
        // PR 리뷰에서 넘어온 경우에만 자동 시작: 그 PR 파일로 리뷰
        const files = await api.getPrFiles(loc.state.prId);
        const front = files.find(isFrontend);
        if (front) {
          setPreviewCode(front.code);
          setDockOpen(true);
        }
        runReview(
          files.map((f) => `// ${f.path}\n${f.code}`).join("\n\n"),
          files,
        );
      }
      // 직접 들어온 경우: 자동 실행 없음 — [🐙 PR 가져오기] / [직접 붙여넣기]로 소스를 먼저 고른다
    })();
  }, []);

  const push = (m) => setMsgs((prev) => [...prev, m]);
  const cogiSays = (list, gap = 650) => {
    list.forEach((m, i) =>
      setTimeout(() => push({ who: "cogi", ...m }), gap * (i + 1)),
    );
    setTimeout(() => setBusy(false), gap * (list.length + 1));
  };

  /* ── ① PR 가져오기: PR 상세에서 넘어왔으면 그 PR, 아니면 대표 PR 의 파일 목록 ── */
  const openPicker = async () => {
    const files = await api.getPrFiles(loc.state?.prId ?? 1);
    setPicker({ files, checked: new Set(files.map((f) => f.path)) }); // 기본 전체 선택
  };

  const importFiles = () => {
    const files = picker.files.filter((f) => picker.checked.has(f.path));
    setPicker(null);
    if (files.length === 0) return;
    const front = files.find(isFrontend);
    if (front) {
      setPreviewCode(front.code);
      setDockOpen(true);
    } // 디자인이 바로 보이게 도크 자동 오픈
    runReview(files.map((f) => `// ${f.path}\n${f.code}`).join("\n\n"), files);
  };

  /* ── ② 리뷰 시작 — 여기서부터 모델 잠금 ── */
  const runReview = async (codeText: string, files?: any[]) => {
    if (!spendCredit(modelWeight(model))) return;
    const hasFront = files?.some(isFrontend) || /<[a-z][^>]*>/i.test(codeText); // 미리보기 힌트용
    if (files)
      push({
        who: "me",
        text: `🐙 PR #42 에서 ${files.length}개 파일 가져옴:\n${files.map((f) => "· " + f.path).join("\n")}`,
      });
    else push({ who: "me", text: codeText, isCode: true });
    setBusy(true);
    try {
      const res = await api.pasteReview(codeText, language, model);
      setReviewId(res.reviewId ?? 1);
      cogiSays([
        {
          text: `${MODEL_TIERS.find((m) => m.name === model)?.label}(으)로 봤어요. 짚을 게 ${res.issues.length}건!`,
        },
        ...res.issues.map((it) => ({ issue: it })),
        {
          text: hasFront
            ? "프론트 코드가 있네요 — 위의 [▶ 미리보기]를 열면 화면을 직접 만지면서 고칠 수 있어요."
            : "더 궁금한 건 그대로 물어보세요. 같은 유형 3회면 약점 통계로 승격돼요.",
        },
      ]);
    } catch {
      refundCredit(modelWeight(model)); // 서버도 @Transactional 롤백으로 안 썼으니 로컬도 원복
      setBusy(false);
    }
  };

  /* ── ③ 후속 질문 — 이것도 AI 호출이라 ⚡1 ── */
  const runQuestion = async (q) => {
    if (!spendCredit(1)) return;
    push({ who: "me", text: q });
    setBusy(true);
    try {
      const res = await api.askReviewQuestion(reviewId, q);
      push({ who: "cogi", text: res.answer });
    } catch {
      refundCredit(1);
    } finally {
      setBusy(false);
    }
  };

  const send = () => {
    const v = input.trim();
    if (!v || busy) return;
    setInput("");
    if (reviewId === null) {
      // 붙여넣은 게 HTML 이면 미리보기 대상으로도 잡아준다
      if (/<[a-z][^>]*>/i.test(v)) setPreviewCode(v);
      runReview(v);
    } else runQuestion(v);
  };

  const onFile = async (f) => {
    if (!f || busy || reviewId !== null) return;
    if (!spendCredit(modelWeight(model))) return;
    push({ who: "me", text: `📁 ${f.name}` });
    setBusy(true);
    try {
      const res = await api.uploadReview(f, language, model);
      setReviewId(res.reviewId ?? 1);
      cogiSays([
        { text: `파일 잘 받았어요! 짚을 게 ${res.issues.length}건.` },
        ...res.issues.map((it) => ({ issue: it })),
      ]);
    } catch {
      refundCredit(modelWeight(model));
      setBusy(false);
    }
  };

  /* ── 새 리뷰: 모든 맥락 리셋 → 모델 다시 선택 가능 ── */
  const resetAll = () => {
    setMsgs([]);
    setReviewId(null);
    setPreviewCode(null);
    setDockOpen(false);
    setInput("");
  };

  return (
    <main className="app-main">
      <PageHead
        badge="STUDIO"
        badgeCls="co"
        title="리뷰 스튜디오"
        lead={
          "PR을 가져오거나 코드를 붙여넣고, 코기와 대화하며 다듬는 작업대예요.\n프론트 코드는 미리보기를 열어 직접 만질 수 있어요."
        }
      />

      {/* ── 미리보기 도크: 채팅 상단, 접었다 펴는 토글 (FR-47~49) ── */}
      {previewCode !== null && (
        <div className="panel flush" style={{ marginBottom: 18 }}>
          <button
            className="dock-toggle"
            onClick={() => setDockOpen((o) => !o)}
          >
            {dockOpen ? "▼ 미리보기 접기" : "▶ 미리보기 열기"}
            <span className="note sm">
              드래그·색상·크기·글자까지 — 편집하면 코드가 실시간으로 바뀝니다
            </span>
          </button>
          {dockOpen && (
            <PreviewDock code={previewCode} onCode={setPreviewCode} />
          )}
        </div>
      )}

      <div className="panel flush chat-panel">
        <div className="chat-thread" ref={threadRef}>
          {msgs.length === 0 && (
            /* ── ① 소스 선택 (빈 상태) ── */
            <div className="empty">
              <p style={{ fontSize: 26 }}>🐕</p>
              <p style={{ marginBottom: 18 }}>
                어떤 코드를 봐드릴까요? 백엔드든 프론트든 다 환영이에요.
                <br />
                GitHub PR을 가져오거나, 아래 입력창에 바로 코드를 붙여넣으세요.
              </p>
              {insufficientCredit && (
                <p className="model-hint" style={{ marginBottom: 12 }}>
                  ⚡ 오늘 크레딧을 다 썼어요 · 자정에 초기화돼요
                </p>
              )}
              <div className="row" style={{ justifyContent: "center" }}>
                <button
                  className="btn co sm"
                  onClick={openPicker}
                  disabled={insufficientCredit}
                >
                  🐙 GitHub PR 가져오기
                </button>
              </div>
            </div>
          )}
          {msgs.map((m, i) =>
            m.issue ? (
              <div key={i} className="chat-bubble cogi">
                <div className="row" style={{ gap: 8, marginBottom: 8 }}>
                  <SevChip sev={m.issue.severity} />
                  <span className="chip navy">{catKo(m.issue.category)}</span>
                </div>
                {m.issue.codeSnippet && (
                  <pre className="codebox snippet">
                    {m.issue.codeSnippet.join("\n")}
                  </pre>
                )}
                <div className="issue-desc">{renderDescription(m.issue.description)}</div>
                {/* #6 이슈 판정은 아래 고정 배너에서만 함 — 채팅창은 기록 보기 전용(읽기 전용) */}
                {verdicts[m.issue.id] && (
                  <span
                    className={`chip ${verdicts[m.issue.id] === "RESOLVED" ? "low" : "mid"}`}
                    style={{ marginTop: 10, display: "inline-block" }}
                  >
                    {verdicts[m.issue.id] === "RESOLVED"
                      ? "✔ 고치기로 함 (해결 요청)"
                      : "🙋 의도한 코드 (무시 요청)"}
                  </span>
                )}
              </div>
            ) : (
              <div key={i} className={`chat-bubble ${m.who}`}>
                {m.isCode ? (
                  <pre>{m.text}</pre>
                ) : (
                  <p style={{ whiteSpace: "pre-line" }}>{m.text}</p>
                )}
              </div>
            ),
          )}
          {busy && (
            <div className="chat-bubble cogi typing">
              {reviewId === null ? "🔍 코드 분석 중" : "🤔 답변 작성 중"}
              <i>.</i>
              <i>.</i>
              <i>.</i>
            </div>
          )}
        </div>

        {/* #6 완료 배너 — 스레드 밖(하단 고정)이라 이슈 말풍선이 항상 위에 보인다 */}
        {(() => {
          const issues = msgs.filter((m) => m.issue).map((m) => m.issue);
          if (issues.length === 0) return null;
          const done = issues.filter((it) => verdicts[it.id]).length;
          const all = done === issues.length;
          const next = issues.find((it) => !verdicts[it.id]); // 다음 미결정 이슈
          return (
            <div className={`studio-done ${all ? "ready" : ""}`}>
              {all ? (
                <span>{`✅ 지적 ${issues.length}건을 모두 확인했어요. 아래 버튼으로 리뷰를 마치면 결과가 PR 리뷰에 정리됩니다.`}</span>
              ) : (
                <div className="next-issue">
                  <div
                    className="row"
                    style={{ gap: 8, alignItems: "center", flexWrap: "wrap" }}
                  >
                    <b>{`${done}/${issues.length} 결정`}</b>
                    <SevChip sev={next.severity} />
                    <span className="chip navy">{catKo(next.category)}</span>
                    <span className="mono xs">
                      {next.filePath}:{next.lineNumber}
                    </span>
                  </div>
                  {/* 설명·코드는 위 채팅 기록에 이미 있음 — 여긴 빠른 결정용이라 슬림하게 유지 */}
                  <div className="row" style={{ gap: 8 }}>
                    <button
                      className="btn wh sm"
                      onClick={() =>
                        setVerdicts((v) => ({ ...v, [next.id]: "IGNORED" }))
                      }
                    >
                      🙋 의도했어요
                    </button>
                    <button
                      className="btn co sm"
                      onClick={() =>
                        setVerdicts((v) => ({ ...v, [next.id]: "RESOLVED" }))
                      }
                    >
                      ✔ 고칠게요
                    </button>
                  </div>
                </div>
              )}
              <button
                className="btn co sm"
                disabled={!all || busy}
                onClick={async () => {
                  setBusy(true);
                  try {
                    await Promise.all(
                      issues.map((it) =>
                        api.finalizeIssue(it.id, verdicts[it.id]),
                      ),
                    );
                    notify(
                      "리뷰를 완료했어요. PR 리뷰에서 정리된 결과를 확인하세요",
                    );
                    nav("/app/prs");
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                리뷰 완료 → PR 리뷰에서 확인
              </button>
            </div>
          );
        })()}

        {/* ── 입력 영역: 입력창이 주인공(전체 폭), 도구는 아래 한 줄로 ── */}
        <div className="chat-input">
          <textarea
            rows={reviewId === null ? 4 : 2}
            value={input}
            spellCheck={false}
            disabled={insufficientCredit}
            placeholder={
              insufficientCredit
                ? "오늘 크레딧을 다 썼어요 · 자정에 초기화돼요"
                : reviewId === null
                ? "리뷰 받을 코드를 붙여넣으세요 (Ctrl+Enter 전송)"
                : "후속 질문을 입력하세요 (Ctrl+Enter 전송)"
            }
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) send();
            }}
          />

          <div className="chat-tools">
            {reviewId === null && (
              <select
                value={language}
                onChange={(e) => setLanguage(e.target.value)}
                aria-label="언어"
              >
                {[
                  "TypeScript",
                  "JavaScript",
                  "Java",
                  "Python",
                  "Kotlin",
                  "Go",
                  "HTML",
                ].map((l) => (
                  <option key={l}>{l}</option>
                ))}
              </select>
            )}
            {/* 모델: 시작 전 자유 선택(상위 티어는 잠금 표시) / 시작 후 통째 잠금 + 안내 */}
            <span className="model-wrap">
              <select
                value={model}
                disabled={reviewId !== null}
                aria-label="AI 모델"
                onChange={(e) => setModel(e.target.value)}
              >
                {MODEL_TIERS.map((m) => (
                  <option
                    key={m.name}
                    value={m.name}
                    disabled={m.tier > myTier}
                  >
                    {m.label}
                    {m.tier > myTier
                      ? ` — ${Object.keys(PLAN_TIER).find((k) => PLAN_TIER[k] === m.tier)} 필요`
                      : ""}
                  </option>
                ))}
              </select>
              {reviewId !== null && (
                <span className="model-hint">
                  모델을 바꾸려면 새 리뷰를 시작하세요
                </span>
              )}
            </span>

            {insufficientCredit && (
              <span className="model-hint">
                오늘 크레딧 소진 · 자정 초기화
              </span>
            )}
            <span className="ml-auto" />
            {reviewId === null ? (
              <button
                className="btn wh sm"
                title={insufficientCredit ? "오늘 크레딧을 다 썼어요" : "파일 업로드"}
                disabled={insufficientCredit}
                onClick={() => fileRef.current?.click()}
              >
                📁 파일
              </button>
            ) : (
              <button
                className="btn wh sm"
                title="맥락을 비우고 처음부터 — 모델도 다시 고를 수 있어요"
                onClick={resetAll}
              >
                + 새 리뷰
              </button>
            )}
            <button
              className="btn co sm"
              onClick={send}
              disabled={busy || !input.trim() || insufficientCredit}
            >
              {reviewId === null ? `리뷰 시작 (⚡${modelWeight(model)})` : "질문 (⚡1)"}
            </button>
          </div>
        </div>
      </div>

      <input
        ref={fileRef}
        type="file"
        accept=".ts,.tsx,.js,.jsx,.java,.py,.kt,.go,.html,.css,.txt"
        hidden
        onChange={(e) => onFile(e.target.files?.[0])}
      />

      {/* ── PR 파일 선택 모달: 다중 선택 or 전체 ── */}
      {picker && (
        <div className="modal-mask" onClick={() => setPicker(null)}>
          <div
            className="modal"
            role="dialog"
            aria-modal="true"
            onClick={(e) => e.stopPropagation()}
          >
            <h3>🐙 PR #42 — 가져올 파일 선택</h3>
            <p className="note sm" style={{ marginBottom: 14 }}>
              여러 개 선택할 수 있어요. 프론트 파일이 포함되면 미리보기도
              열립니다.
            </p>
            <label
              className="check-row"
              style={{ fontWeight: 700, marginBottom: 10 }}
            >
              <input
                type="checkbox"
                checked={picker.checked.size === picker.files.length}
                onChange={(e) =>
                  setPicker((p) => ({
                    ...p,
                    checked: new Set(
                      e.target.checked ? p.files.map((f) => f.path) : [],
                    ),
                  }))
                }
              />
              전체 선택
            </label>
            {picker.files.map((f) => (
              <label
                key={f.path}
                className="check-row"
                style={{ marginBottom: 8 }}
              >
                <input
                  type="checkbox"
                  checked={picker.checked.has(f.path)}
                  onChange={() =>
                    setPicker((p) => {
                      const next = new Set(p.checked);
                      next.has(f.path) ? next.delete(f.path) : next.add(f.path);
                      return { ...p, checked: next };
                    })
                  }
                />
                <span className="mono" style={{ fontSize: 12.5 }}>
                  {f.path}
                </span>
                <span className={`chip ${isFrontend(f) ? "low" : "navy"}`}>
                  {isFrontend(f) ? "프론트" : "백엔드"}
                </span>
              </label>
            ))}
            {insufficientCredit && (
              <p className="model-hint" style={{ marginTop: 10 }}>
                ⚡ 오늘 크레딧을 다 썼어요 · 자정에 초기화돼요
              </p>
            )}
            <div className="row" style={{ marginTop: 20 }}>
              <button className="btn wh sm" onClick={() => setPicker(null)}>
                취소
              </button>
              <button
                className="btn co sm ml-auto"
                onClick={importFiles}
                disabled={picker.checked.size === 0 || insufficientCredit}
              >
                {picker.checked.size}개 가져와서 리뷰 (⚡{modelWeight(model)})
              </button>
            </div>
          </div>
        </div>
      )}

      <p className="note sm" style={{ marginTop: 14 }}>
        결과는 약점 통계에 쌓여요.{" "}
        <Link to="/app/weakness" className="link-line">
          약점 통계 →
        </Link>{" "}
        · 팀 결재가 필요한 PR 이슈는{" "}
        <Link to="/app/prs" className="link-line">
          PR 리뷰 →
        </Link>
      </p>
    </main>
  );
}
