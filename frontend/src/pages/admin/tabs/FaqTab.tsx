/*
 * [관리자 탭] FAQ 관리 — 등록 + (클릭해 펼침 → 수정 버튼으로 편집) + 삭제 + 노출토글
 * 컨테이너(Admin.tsx)가 목록과 CRUD 핸들러를 갖고, 여기선 입력 폼 + 목록 UI만 담당.
 * 목록은 아코디언: 헤더 클릭 → 답변 펼침(읽기전용) → [수정] 눌러야 입력 필드로 편집.
 * 편집은 editDraft(사본)에서만 → [취소] 시 원본 그대로, [저장] 시에만 반영.
 */
import { useState } from 'react';

const EMPTY = { question: '', answer: '', category: '', visible: true };

// 분류/노출 한 줄 (신규·수정 공용). 순서는 등록순(id)이라 정렬 필드 없음.
function MetaRow({ v, on }) {
  return (
    <div className="row" style={{ gap: 16, alignItems: 'flex-end', flexWrap: 'wrap' }}>
      <div style={{ flex: 1, minWidth: 180 }}>
        <label>분류</label>
        <input type="text" placeholder="예: 계정, 결제, 리뷰" value={v.category ?? ''}
          onChange={(e) => on('category', e.target.value)} />
      </div>
      <label className="check-row" style={{ paddingBottom: 12 }}>
        <input type="checkbox" checked={v.visible} onChange={(e) => on('visible', e.target.checked)} />
        <span style={{ marginLeft: 8 }}>사용자에게 노출</span>
      </label>
    </div>
  );
}

export default function FaqTab({ faqs, onCreate, onUpdate, onDelete }) {
  const [draft, setDraft] = useState(EMPTY);
  const [openId, setOpenId] = useState(null);       // 펼친 FAQ id
  const [editId, setEditId] = useState(null);       // 편집 중인 FAQ id
  const [editDraft, setEditDraft] = useState(EMPTY); // 편집 사본(취소 시 원본 유지)
  const patchDraft = (k, val) => setDraft((d) => ({ ...d, [k]: val }));
  const patchEdit = (k, val) => setEditDraft((d) => ({ ...d, [k]: val }));

  const startEdit = (f) => { setEditDraft({ ...f }); setEditId(f.id); };

  return (
    <>
      {/* 새 FAQ 추가 */}
      <div className="panel">
        <h3>➕ 새 FAQ 추가</h3>
        <p className="note sm">노출을 켜면 상단 FAQ 페이지와 푸터 링크에서 바로 보여요.</p>
        <div className="form">
          <div>
            <label>질문</label>
            <input type="text" value={draft.question} onChange={(e) => patchDraft('question', e.target.value)} />
          </div>
          <div>
            <label>답변</label>
            <textarea value={draft.answer} onChange={(e) => patchDraft('answer', e.target.value)} />
          </div>
          <MetaRow v={draft} on={patchDraft} />
          <button className="btn co" disabled={!draft.question.trim() || !draft.answer.trim()}
            onClick={async () => { await onCreate(draft); setDraft(EMPTY); }}>
            추가
          </button>
        </div>
      </div>

      {/* 기존 FAQ 목록 */}
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline', margin: '36px 2px 4px' }}>
        <b>등록된 FAQ</b>
        <span className="note sm">{faqs.length}개</span>
      </div>

      {faqs.length === 0
        ? <div className="empty"><p>등록된 FAQ가 없어요. 위에서 추가해보세요.</p></div>
        : (
          <div className="faq-list" style={{ marginTop: 12 }}>
            {faqs.map((f) => {
              const isOpen = openId === f.id;
              const isEditing = editId === f.id;
              return (
                <div key={f.id} className={`faq-item${isOpen ? ' open' : ''}`}>
                  {/* 헤더 — 클릭하면 펼침 */}
                  <button type="button" className="faq-q"
                    onClick={() => { setOpenId(isOpen ? null : f.id); setEditId(null); }}>
                    {f.category && <span className="chip navy sm">{f.category}</span>}
                    <span className={`chip sm ${f.visible ? 'low' : 'gray'}`}>{f.visible ? '노출' : '숨김'}</span>
                    <span className="faq-q-text">{f.question}</span>
                    <span className="faq-caret">{isOpen ? '−' : '+'}</span>
                  </button>

                  {isOpen && (
                    <div className="faq-a">
                      {!isEditing ? (
                        // 읽기전용 — 수정 버튼을 눌러야 편집 가능
                        <>
                          <div style={{ whiteSpace: 'pre-wrap', marginBottom: 16 }}>{f.answer}</div>
                          <div className="row" style={{ gap: 8 }}>
                            <button className="btn co sm" onClick={() => startEdit(f)}>수정</button>
                            <button className="btn wh sm" style={{ color: 'var(--coral)' }} onClick={() => onDelete(f)}>삭제</button>
                          </div>
                        </>
                      ) : (
                        // 편집 모드
                        <div className="form">
                          <div>
                            <label>질문</label>
                            <input type="text" value={editDraft.question} onChange={(e) => patchEdit('question', e.target.value)} />
                          </div>
                          <div>
                            <label>답변</label>
                            <textarea value={editDraft.answer} onChange={(e) => patchEdit('answer', e.target.value)} />
                          </div>
                          <MetaRow v={editDraft} on={patchEdit} />
                          <div className="row" style={{ gap: 8 }}>
                            <button className="btn co sm"
                              disabled={!editDraft.question.trim() || !editDraft.answer.trim()}
                              onClick={async () => { await onUpdate(editDraft); setEditId(null); }}>
                              저장
                            </button>
                            <button className="btn wh sm" onClick={() => setEditId(null)}>취소</button>
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
    </>
  );
}
