/*
 * 모바일 전용 리뷰 스튜디오.
 * 데스크톱은 페이지 안에 채팅 패널이 얹힌 구조라, 폰에서는 입력창이 화면 밖으로 밀려 안 보인다.
 * 화면 전체를 채팅앱처럼 쓴다 — 말풍선만 스크롤하고 입력창은 아래에 고정, PR 피커는 바텀시트.
 * 기능은 데스크톱과 같다 — PR 가져오기(레포→PR→파일)·붙여넣기·파일 업로드·모델 선택/잠금·
 * 후속 질문·이슈 판정·리뷰 완료·새 리뷰·미리보기 도크·PR 판정 이어받기.
 */
import { useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import { renderDescription } from "../../components/ui";
import { renderQuestion } from "../../components/cardText";
import { MODEL_TIERS, PLAN_TIER, catKo, sevKo } from "../../data/constants";
import PreviewDock from "../review/PreviewDock";
import "../../styles/mobile/studio.css";

// 뱃지는 "프론트 파일이냐"만 본다. 미리보기 가능 여부(isPreviewable)와는 다른 질문이다
const isFrontend = (f) =>
  /\.(html?|css|s[ac]ss|less|jsx?|tsx?|vue|svelte)$/i.test(f.path) || f.kind === "frontend";
// 미리보기는 iframe에 문서를 통째로 넣는 방식이라 뿌리가 될 수 있는 건 HTML뿐이다
const isPreviewable = (f) => /\.html?$/i.test(f.path || f.name || ""); // PR 파일은 path, 올린 파일은 name
// 붙여넣은 글이 HTML 문서인지. 예전엔 /<[a-z][^>]*>/ 하나로 봐서 자바 제네릭(List<String>)까지
// 걸렸고, 프론트가 한 줄도 없는 PR에도 "미리보기를 여세요"가 떴다
const looksHtml = (t) =>
  /<(!doctype\s+html|html|head|body|div|section|main|header|footer|nav|form|table|ul|ol|p|span|button|img|h[1-6])\b/i.test(t);
// 작업대 상태를 담아두는 자리 (데스크톱과 같은 키 — 화면만 다르고 같은 작업대다)
const STUDIO_KEY = "cogi-studio";

export default function MobileStudio() {
  const { user } = useAuth();
  const { spendCredit, refundCredit, notify, S, creditLimit } = useGame();
  const location = useLocation();
  const fileRef = useRef(null);
  const threadRef = useRef(null);
  const loadedPrRef = useRef(false); // StrictMode 이중 실행에도 PR 이어받기는 한 번만

  const myTier = PLAN_TIER[user.planName] ?? 1;
  const modelWeight = (m) => MODEL_TIERS.find((t) => t.name === m)?.tier ?? 1;
  const remainingCredit = creditLimit - S.creditUsed;

  // 화면을 떠나면 컴포넌트가 언마운트돼 대화가 날아갔다. 탭 안에서만 살아 있으면 되니 sessionStorage
  const saved = (() => {
    try { return JSON.parse(sessionStorage.getItem(STUDIO_KEY) || "null") ?? {}; }
    catch { return {}; }
  })();

  const [msgs, setMsgs] = useState(saved.msgs ?? []);
  const [input, setInput] = useState(saved.input ?? "");
  const [model, setModel] = useState(saved.model ?? MODEL_TIERS[0].name);
  const [busy, setBusy] = useState(false);
  const [reviewId, setReviewId] = useState(saved.reviewId ?? null); // null = 시작 전 (모델 변경 가능)
  const [verdicts, setVerdicts] = useState(saved.verdicts ?? {});
  const [finalized, setFinalized] = useState(saved.finalized ?? false);
  const [isPrReview, setIsPrReview] = useState(saved.isPrReview ?? false);
  const [picker, setPicker] = useState(null);
  const [previewCode, setPreviewCode] = useState(saved.previewCode ?? null);
  const [dockOpen, setDockOpen] = useState(saved.dockOpen ?? false);
  const [previewRepoId, setPreviewRepoId] = useState(saved.previewRepoId ?? null); // 부족한 파일을 찾을 레포
  const [favSkills, setFavSkills] = useState(null); // 즐겨찾기 스킬. null = 아직 안 받음
  const [favOpen, setFavOpen] = useState(false); // 즐겨찾기 목록 열림 여부

  const actionCost = reviewId === null ? modelWeight(model) : 1;
  const insufficientCredit = remainingCredit < actionCost;

  // 새 말풍선 → 스레드 안에서만 아래로 (페이지 전체를 끌고 내려가면 안 된다)
  useEffect(() => {
    const t = threadRef.current;
    if (t) t.scrollTop = t.scrollHeight;
  }, [msgs, busy]);

  const push = (m) => setMsgs((prev) => [...prev, m]);

  // 대화·리뷰 맥락을 담아둔다. busy와 picker는 뺐다 — 떠나는 순간 끝난 상태라 되살리면 버튼이 잠긴 채로 뜬다
  useEffect(() => {
    try {
      sessionStorage.setItem(STUDIO_KEY, JSON.stringify({
        msgs, input, model, reviewId, verdicts, finalized, isPrReview,
        previewCode, dockOpen, previewRepoId,
      }));
    } catch { /* 용량 초과 등 — 저장 못 해도 화면은 그대로 돈다 */ }
  }, [msgs, input, model, reviewId, verdicts, finalized, isPrReview, previewCode, dockOpen, previewRepoId]);

  // 즐겨찾기 목록은 [★ 즐겨찾기]를 열 때 받는다. 열 때마다 다시 받아야
  // 스킬 추천 화면에서 방금 별을 단 게 이 목록에도 바로 뜬다
  const openFavSkills = async () => {
    if (favOpen) return setFavOpen(false);
    setFavOpen(true);
    try { setFavSkills(await api.getFavoriteSkills()); }
    catch { setFavSkills([]); } // 목록만 비운다 — 채팅 자체를 막을 이유는 없다
  };

  // AI가 만들어 준 스킬은 붙여넣을 프롬프트를 들고 있으니 그걸 그대로 쓰고,
  // 큐레이션 스킬은 prompt가 없어서 제목으로 문장을 만든다.
  // 리뷰 시작 전에는 붙여넣은 코드가 이미 있을 수 있어 덮지 않고 뒤에 붙인다
  const useFavSkill = (s) => {
    const line = s.prompt?.trim() || `${s.title} 관점에서 이 코드를 점검해줘.`;
    setInput((prev) => (prev.trim() ? `${prev.trimEnd()}

${line}` : line));
    setFavOpen(false);
  };

  // PR 상세에서 넘어오면 새 리뷰 대신 그 PR의 미판정(OPEN) 이슈만 이어받는다
  useEffect(() => {
    const prId = location.state?.prId;
    if (!prId || loadedPrRef.current) return;
    loadedPrRef.current = true;
    (async () => {
      const res = await api.getPrReview(prId);
      const openIssues = res.issues.filter((it) => it.status === "OPEN");
      if (openIssues.length === 0) {
        notify("이 PR엔 판정할 지적이 없어요.");
        return;
      }
      setReviewId(res.reviewHistory?.[0]?.reviewId ?? null);
      setIsPrReview(true);
      push({ who: "cogi", text: `PR 판정을 이어갈게요 — 아직 결정 안 한 지적 ${openIssues.length}건이에요.` });
      openIssues.forEach((it) => push({ issue: it }));
    })();
  }, [location.state]); // eslint-disable-line react-hooks/exhaustive-deps

  const cogiSays = (list, gap = 650) => {
    list.forEach((m, i) => setTimeout(() => push({ who: "cogi", ...m }), gap * (i + 1)));
    setTimeout(() => setBusy(false), gap * (list.length + 1));
  };

  /* ── ① PR 가져오기: 레포 → PR → 파일 ── */
  const openPicker = async () => {
    // 데스크톱과 같다 — 연동해 둔 레포 + 내 GitHub 레포를 함께 띄운다.
    // 연동분만 보이면 아직 안 붙인 레포의 PR을 가져올 길이 없다
    const [linked, mine] = await Promise.all([
      api.getMyLinkedRepos().catch(() => []),
      api.getGithubRepos().catch(() => []), // GitHub 미연동이면 400 — 그땐 연동분만
    ]);
    const repoIdByGh = Object.fromEntries(linked.map((r) => [String(r.githubRepoId), r.repoId]));
    const repos = mine.map((g) => ({
      githubRepoId: g.githubRepoId,
      repoName: g.repoName,
      repoId: repoIdByGh[String(g.githubRepoId)] ?? null,
    }));
    // 초대로 들어온 팀 레포처럼 내 GitHub 목록엔 없지만 연동된 것도 빠뜨리지 않는다
    linked.forEach((r) => {
      if (!repos.some((x) => String(x.githubRepoId) === String(r.githubRepoId))) {
        repos.push({ githubRepoId: r.githubRepoId, repoName: r.repoName, repoId: r.repoId });
      }
    });
    if (repos.length === 0) {
      notify("레포가 없어요. 마이페이지에서 GitHub을 먼저 연동해주세요.");
      return;
    }
    setPicker({ step: "repo", repos });
  };

  const pickRepo = async (repo) => {
    // 아직 안 붙은 레포면 여기서 붙이고 이어간다
    let repoId = repo.repoId;
    try {
      if (!repoId) repoId = (await api.linkRepo(repo.githubRepoId)).repoId;
      const prs = await api.getRepoPrs(repoId);
      setPicker((p) => ({ ...p, step: "pr", repo: { ...repo, repoId }, prs }));
    } catch (e) {
      notify(e.message || "이 레포의 PR을 가져오지 못했어요");
    }
  };

  const pickPr = async (pr) => {
    const files = await api.getRepoPrFiles(picker.repo.repoId, pr.number);
    setPicker((p) => ({ ...p, step: "files", pr, files, checked: new Set(files.map((f) => f.path)) }));
  };

  // 피커가 주는 f.code는 전체 내용이 아니라 GitHub의 patch(diff)다. 그대로 띄우면 +/- 글자만 나온다.
  // 그래서 HTML 원본을 다시 받아온다. 폰에서는 도크를 자동으로 펴지 않는다 — 채팅이 가려진다
  const openPreviewFor = async (files, repoId) => {
    const front = files.find(isPreviewable);
    if (!front) return;
    setPreviewCode(front.code); // 원본이 오기 전까진 diff라도
    try {
      const file = await api.getRepoFileContent(repoId, front.path);
      if (file?.content) setPreviewCode(file.content);
    } catch (e) {
      notify(e.message || "원본 파일을 못 받아 diff 상태로 보여드려요");
    }
  };

  const importFiles = () => {
    const files = picker.files.filter((f) => picker.checked.has(f.path));
    const { repo, pr } = picker;
    setPicker(null);
    if (files.length === 0) return;
    setPreviewRepoId(repo.repoId); // 미리보기가 부족한 파일을 이 레포에서 찾는다
    openPreviewFor(files, repo.repoId);
    runReview(files.map((f) => `// ${f.path}\n${f.code}`).join("\n\n"), files, {
      repoId: repo.repoId, prNumber: pr.number, title: pr.title, authorLogin: pr.authorLogin,
    });
  };

  /* ── ② 리뷰 시작 — 여기서부터 모델 잠금 ── */
  const runReview = async (codeText: string, files?: any[], prMeta?: any) => {
    if (!spendCredit(modelWeight(model))) return;
    // 안내 문구는 "미리보기를 실제로 열 수 있을 때"만 띄운다.
    // isFrontend(css 포함)로 보면 CSS만 있는 PR에도 뜨는데, 그건 뿌리 문서가 없어 못 연다
    const hasFront = files ? files.some(isPreviewable) : looksHtml(codeText);
    if (files) {
      push({ who: "me", text: `🐙 PR #${prMeta.prNumber} 에서 ${files.length}개 파일 가져옴:\n${files.map((f) => "· " + f.path).join("\n")}` });
      setIsPrReview(true);
    } else push({ who: "me", text: codeText, isCode: true });
    setBusy(true);
    try {
      const res = prMeta
        ? await api.reviewImportedPr(prMeta.repoId, prMeta.prNumber, codeText, model, prMeta.title, prMeta.authorLogin)
        : await api.pasteReview(codeText, model);
      setReviewId(res.reviewId ?? 1);
      if (res.analyzable === false) {
        // 서버가 이미 환불했으니 로컬 표시도 맞춘다
        refundCredit(modelWeight(model));
        cogiSays([{ text: res.summary || "코드가 아니라서 분석할 수 없었어요. 크레딧은 안 깎였어요." }]);
      } else {
        cogiSays([
          { text: `${MODEL_TIERS.find((m) => m.name === model)?.label}(으)로 봤어요. 짚을 게 ${res.issues.length}건!` },
          ...res.issues.map((it) => ({ issue: it })),
          {
            text: hasFront
              ? "프론트 코드가 있네요 — 위의 [▶ 미리보기]를 열면 화면을 직접 만지면서 고칠 수 있어요."
              : "더 궁금한 건 그대로 물어보세요. 같은 유형 3회면 약점 통계로 승격돼요.",
          },
        ]);
      }
    } catch {
      refundCredit(modelWeight(model));
      push({ who: "cogi", text: "리뷰 처리 중 문제가 생겼어요. 크레딧은 안 깎였어요 — 다시 시도해주세요." });
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
      push({ who: "cogi", text: "답변 중 문제가 생겼어요. 크레딧은 안 깎였어요 — 다시 물어봐주세요." });
    } finally {
      setBusy(false);
    }
  };

  const send = () => {
    const v = input.trim();
    if (!v || busy) return;
    setInput("");
    if (reviewId === null) {
      if (looksHtml(v)) setPreviewCode(v);
      runReview(v);
    } else runQuestion(v);
  };

  const onFile = async (f) => {
    if (!f || busy || reviewId !== null) return;
    if (!spendCredit(modelWeight(model))) return;
    push({ who: "me", text: `📁 ${f.name}` });
    setBusy(true);
    // 올린 게 HTML이면 미리보기 대상으로 잡아둔다. 폰은 도크를 자동으로 펴지 않는다 — 채팅이 가려진다
    if (isPreviewable(f)) {
      try { setPreviewCode(await f.text()); } catch { /* 못 읽으면 리뷰만 진행 */ }
    }
    try {
      const res = await api.uploadReview(f, model);
      setReviewId(res.reviewId ?? 1);
      if (res.analyzable === false) {
        refundCredit(modelWeight(model));
        cogiSays([{ text: res.summary || "코드가 아니라서 분석할 수 없었어요. 크레딧은 안 깎였어요." }]);
      } else {
        cogiSays([
          { text: `파일 잘 받았어요! 짚을 게 ${res.issues.length}건.` },
          ...res.issues.map((it) => ({ issue: it })),
        ]);
      }
    } catch {
      refundCredit(modelWeight(model));
      push({ who: "cogi", text: "리뷰 처리 중 문제가 생겼어요. 크레딧은 안 깎였어요 — 다시 시도해주세요." });
      setBusy(false);
    }
  };

  const resetAll = () => {
    sessionStorage.removeItem(STUDIO_KEY); // 담아둔 것도 같이 버린다
    setMsgs([]);
    setReviewId(null);
    setPreviewCode(null);
    setDockOpen(false);
    setPreviewRepoId(null);
    setInput("");
    setFinalized(false);
    setIsPrReview(false);
  };

  const issues = msgs.filter((m) => m.issue).map((m) => m.issue);
  const done = issues.filter((it) => verdicts[it.id]).length;
  const allDone = issues.length > 0 && done === issues.length;
  const next = issues.find((it) => !verdicts[it.id]);

  const finalize = async () => {
    setBusy(true);
    try {
      await Promise.all(issues.map((it) => api.finalizeIssue(it.id, verdicts[it.id])));
      setFinalized(true); // 같은 판정을 중복 저장할 이유가 없다
      // PR 리뷰의 CRITICAL을 "고칠게요"로 확정하면 서버가 팀장 승인 대기로 돌려놓는다
      const needsApproval = isPrReview && issues.some((it) => verdicts[it.id] === "RESOLVED" && it.severity === "CRITICAL");
      notify(needsApproval
        ? "리뷰를 완료했어요. 심각도 높은 이슈는 팀장 승인 후 '해결됨'으로 반영돼요."
        : "리뷰를 완료했어요. 판정이 저장됐어요.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="mapp mst">
      {/* 미리보기 도크 — 폰에서는 화면을 많이 먹으니 접힌 채로 둔다 */}
      {previewCode !== null && (
        <button className="mst-dock" onClick={() => setDockOpen((o) => !o)}>
          {dockOpen ? "▼ 미리보기 접기" : "▶ 미리보기 열기"}
        </button>
      )}
      {previewCode !== null && dockOpen && (
        <div className="mst-dockbody"><PreviewDock code={previewCode} onCode={setPreviewCode} repoId={previewRepoId} /></div>
      )}

      <div className="mst-thread" ref={threadRef}>
        {msgs.length === 0 && (
          <div className="mst-empty">
            <p className="mst-dog">🐕</p>
            <p>
              어떤 코드를 봐드릴까요?<br />
              GitHub PR을 가져오거나, 아래에 코드를 붙여넣으세요.
            </p>
            {insufficientCredit && <p className="mst-warn">⚡ 오늘 크레딧을 다 썼어요 · 자정에 초기화돼요</p>}
            <button className="btn co sm full" onClick={openPicker} disabled={insufficientCredit}>
              🐙 GitHub PR 가져오기
            </button>
          </div>
        )}

        {msgs.map((m, i) =>
          m.issue ? (
            <div key={i} className="mst-bub cogi">
              <div className="mst-tags">
                <span className={`mst-sev ${m.issue.severity.toLowerCase()}`}>{sevKo(m.issue.severity)}</span>
                <span className="mst-cat">{catKo(m.issue.category)}</span>
              </div>
              {m.issue.codeSnippet && <pre className="codebox mst-snip">{m.issue.codeSnippet.join("\n")}</pre>}
              <div className="mst-desc">{renderDescription(m.issue.description)}</div>
              {/* 판정은 아래 고정 배너에서만 — 여긴 기록 보기 전용 */}
              {verdicts[m.issue.id] && (
                <span className={`mst-verdict ${verdicts[m.issue.id] === "RESOLVED" ? "ok" : "skip"}`}>
                  {verdicts[m.issue.id] === "RESOLVED" ? "✔ 고치기로 함" : "🙋 의도한 코드"}
                </span>
              )}
            </div>
          ) : (
            <div key={i} className={`mst-bub ${m.who}`}>
              {m.isCode ? (
                <pre className="mst-mycode">{m.text}</pre>
              ) : m.who === "cogi" ? (
                // 후속 답변은 마크다운으로 온다. 그냥 뿌리면 ```펜스와 #제목이 글자로 찍힌다
                renderQuestion(m.text)
              ) : (
                <p>{m.text}</p>
              )}
            </div>
          ),
        )}

        {busy && (
          <div className="mst-bub cogi mst-typing">
            {reviewId === null ? "🔍 코드 분석 중" : "🤔 답변 작성 중"}<i>.</i><i>.</i><i>.</i>
          </div>
        )}
      </div>

      {/* 판정 배너 — 스레드 밖이라 이슈 말풍선이 항상 위에 보인다 */}
      {issues.length > 0 && (
        <div className={`mst-done ${allDone ? "ready" : ""}`}>
          {allDone ? (
            <p>✅ 지적 {issues.length}건을 모두 확인했어요.</p>
          ) : (
            <>
              <div className="mst-nexttop">
                <b>{done}/{issues.length} 결정</b>
                <span className={`mst-sev ${next.severity.toLowerCase()}`}>{sevKo(next.severity)}</span>
                <span className="mst-cat">{catKo(next.category)}</span>
              </div>
              <p className="mst-nextfile">{next.filePath}{next.lineNumber ? `:${next.lineNumber}` : ""}</p>
              <div className="mst-picks">
                <button className="btn wh sm" onClick={() => setVerdicts((v) => ({ ...v, [next.id]: "IGNORED" }))}>
                  🙋 의도했어요
                </button>
                <button className="btn co sm" onClick={() => setVerdicts((v) => ({ ...v, [next.id]: "RESOLVED" }))}>
                  ✔ 고칠게요
                </button>
              </div>
            </>
          )}
          <button className="btn co sm full" disabled={!allDone || busy || finalized} onClick={finalize}>
            {finalized ? "완료됨" : "리뷰 완료"}
          </button>
        </div>
      )}

      {/* 입력 — 화면 아래 고정 */}
      <div className="mst-input">
        {/* 즐겨찾기해 둔 스킬 — 버튼으로 접어 둔다. 칩을 늘 깔면 좁은 화면에서 입력창이 밀린다.
            리뷰 시작 전에도 연다 — 어떤 관점으로 볼지 코드와 같이 적어 보낼 수 있다 */}
        <div className="mst-fav">
            <button type="button" className={`mst-fav-btn ${favOpen ? "on" : ""}`} onClick={openFavSkills}>
              ★ 즐겨찾기 스킬{favSkills?.length ? ` ${favSkills.length}` : ""}
              <i>{favOpen ? "▲" : "▼"}</i>
            </button>
            {favOpen && (
              favSkills === null ? (
                <p className="mst-hint">불러오는 중…</p>
              ) : favSkills.length === 0 ? (
                <p className="mst-hint">
                  아직 즐겨찾기한 스킬이 없어요. <Link to="/app/skills">AI 스킬 추천</Link>에서 별을 달면 여기 뜹니다.
                </p>
              ) : (
                <div className="mst-skills">
                  {favSkills.map((s) => (
                    <button key={s.id} className="mst-skill" disabled={busy || insufficientCredit}
                      onClick={() => useFavSkill(s)}>
                      ★ {s.title}
                    </button>
                  ))}
                </div>
              )
          )}
        </div>
        <textarea
          rows={2}
          value={input}
          spellCheck={false}
          disabled={insufficientCredit}
          placeholder={
            insufficientCredit
              ? "오늘 크레딧을 다 썼어요 · 자정에 초기화돼요"
              : reviewId === null
                ? "리뷰 받을 코드를 붙여넣으세요"
                : "후속 질문을 입력하세요"
          }
          onChange={(e) => setInput(e.target.value)}
        />
        <div className="mst-tools">
          {/* 시작 전에는 모델 선택, 시작하면 잠긴다 (대화 맥락이 모델에 묶여 있다) */}
          <select className="mst-model" value={model} disabled={reviewId !== null} aria-label="AI 모델"
            onChange={(e) => setModel(e.target.value)}>
            {MODEL_TIERS.map((m) => (
              <option key={m.name} value={m.name} disabled={m.tier > myTier}>
                {m.label}{m.tier > myTier ? ` — ${Object.keys(PLAN_TIER).find((k) => PLAN_TIER[k] === m.tier)} 필요` : ""}
              </option>
            ))}
          </select>
          {reviewId === null ? (
            <button className="btn wh sm mst-icon" disabled={insufficientCredit}
              onClick={() => fileRef.current?.click()} aria-label="파일 업로드">📁</button>
          ) : (
            <button className="btn wh sm mst-icon" onClick={resetAll} aria-label="새 리뷰">＋</button>
          )}
          <button className="btn co sm mst-send" onClick={send} disabled={busy || !input.trim() || insufficientCredit}>
            {reviewId === null ? `리뷰 (⚡${modelWeight(model)})` : "질문 (⚡1)"}
          </button>
        </div>
        {reviewId !== null && <p className="mst-hint">모델을 바꾸려면 [＋]로 새 리뷰를 시작하세요</p>}
        {insufficientCredit && <p className="mst-hint">오늘 크레딧 소진 · 자정 초기화</p>}
        {msgs.length > 0 && (
          <p className="mst-hint">
            결과는 <Link to="/app/weakness">약점 통계</Link>에 쌓여요 · 팀 결재가 필요한 건 <Link to="/app/prs">PR 리뷰</Link>에서
          </p>
        )}
      </div>

      <input ref={fileRef} type="file" hidden
        accept=".ts,.tsx,.js,.jsx,.java,.py,.kt,.go,.html,.css,.txt"
        onChange={(e) => onFile(e.target.files?.[0])} />

      {/* PR 피커 — 폰에서는 모달 대신 바텀시트 */}
      {picker && (
        <>
          <div className="mst-dim" onClick={() => setPicker(null)} />
          <div className="mst-sheet" role="dialog" aria-modal="true">
            {picker.step === "repo" && (
              <>
                <h3>어느 레포의 PR을 가져올까요?</h3>
                <p className="mnote">아직 연동 안 한 것도 고르면 그 자리에서 붙여드려요.</p>
                <ul className="mst-opts">
                  {picker.repos.map((r) => (
                    <li key={r.githubRepoId}>
                      <button onClick={() => pickRepo(r)}>
                        <b>{r.repoName}</b>
                        {/* 이미 붙은 것과 지금 붙일 것을 구분해 준다 */}
                        <i>{r.repoId ? "연동됨" : "연동 필요"}</i>
                      </button>
                    </li>
                  ))}
                </ul>
                <button className="btn wh sm full" onClick={() => setPicker(null)}>취소</button>
              </>
            )}

            {picker.step === "pr" && (
              <>
                <h3>{picker.repo.repoName}</h3>
                <p className="mnote">{picker.prs.length === 0 ? "열린 PR이 없어요." : "가져올 PR을 고르세요."}</p>
                <ul className="mst-opts">
                  {picker.prs.map((pr) => (
                    <li key={pr.number}>
                      <button onClick={() => pickPr(pr)}>
                        <b>#{pr.number} {pr.title}</b>
                        {pr.authorLogin && <i>@{pr.authorLogin}</i>}
                      </button>
                    </li>
                  ))}
                </ul>
                <button className="btn wh sm full" onClick={() => setPicker((p) => ({ step: "repo", repos: p.repos }))}>
                  ← 레포 다시 선택
                </button>
              </>
            )}

            {picker.step === "files" && (
              <>
                <h3>PR #{picker.pr.number} — 파일 선택</h3>
                <p className="mnote">프론트 파일이 있으면 미리보기도 열 수 있어요.</p>
                <ul className="mst-opts check">
                  <li>
                    <label className="mst-all">
                      <input type="checkbox" checked={picker.checked.size === picker.files.length}
                        onChange={(e) => setPicker((p) => ({
                          ...p, checked: new Set(e.target.checked ? p.files.map((f) => f.path) : []),
                        }))} />
                      전체 선택
                    </label>
                  </li>
                  {picker.files.map((f) => (
                    <li key={f.path}>
                      <label>
                        <input type="checkbox" checked={picker.checked.has(f.path)}
                          onChange={() => setPicker((p) => {
                            const nextSet = new Set(p.checked);
                            nextSet.has(f.path) ? nextSet.delete(f.path) : nextSet.add(f.path);
                            return { ...p, checked: nextSet };
                          })} />
                        <b>{f.path}</b>
                        <i className={isFrontend(f) ? "front" : ""}>{isFrontend(f) ? "프론트" : "백엔드"}</i>
                      </label>
                    </li>
                  ))}
                </ul>
                {insufficientCredit && <p className="mst-warn">⚡ 오늘 크레딧을 다 썼어요 · 자정에 초기화돼요</p>}
                <button className="btn co sm full" onClick={importFiles}
                  disabled={picker.checked.size === 0 || insufficientCredit}>
                  {picker.checked.size}개 가져와서 리뷰 (⚡{modelWeight(model)})
                </button>
                <button className="btn wh sm full mst-second" onClick={() => setPicker(null)}>취소</button>
              </>
            )}
          </div>
        </>
      )}
    </main>
  );
}
