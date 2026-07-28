                  const isMe = mine(m);
                  return (
                    <tr key={m.userId}>
                      <td><b>{handle(m)}{isMe && <span className="chip low tm-me">나</span>}</b></td>
                      <td><span className={`chip ${m.role === 'OWNER' ? 'co' : 'navy'}`}>{m.role === 'OWNER' ? '팀장' : '팀원'}</span></td>
                      <td className="mono xs">{joined(m.joinedAt)}</td>
                      <td>
                        <div className="row tm-acts">
                          {isMe && m.role === 'MEMBER' && (
                            <button className="btn wh sm" onClick={() => leave(r.repoId)}>나가기</button>
                          )}
                          {!isMe && myRole === 'OWNER' && m.role === 'MEMBER' && (
                            <>
                              <button className="btn wh sm" onClick={() => transfer(r.repoId, m)}>위임</button>
                              <button className="btn wh sm" onClick={() => kick(r.repoId, m)}>내보내기</button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            <div className="filter-bar" style={{ marginTop: 14, marginBottom: 0 }}>
              <input
                type="text" placeholder="GitHub 아이디로 팀원 초대"
                value={inviteInput[r.repoId] || ''}
                onChange={(e) => {
                  setInviteInput((p) => ({ ...p, [r.repoId]: e.target.value }));
                  setNeedsEmail((p) => ({ ...p, [r.repoId]: false })); // 아이디를 바꾸면 이전 실패 상태는 초기화
                  setInviteError((p) => ({ ...p, [r.repoId]: '' }));
                }}
                onKeyDown={(e) => e.key === 'Enter' && !needsEmail[r.repoId] && sendInvite(r.repoId)}
              />
              <button
                className="btn wh sm"
                disabled={busy || needsEmail[r.repoId] || !(inviteInput[r.repoId] || '').trim()}
                onClick={() => sendInvite(r.repoId)}
              >초대</button>
            </div>

            {needsEmail[r.repoId] && (
              <div className="filter-bar" style={{ marginTop: 8, marginBottom: 0 }}>
                <input
                  type="email" placeholder="이메일로 초대장 보내기 (예: friend@mail.com)"
                  value={inviteEmail[r.repoId] || ''}
                  onChange={(e) => setInviteEmail((p) => ({ ...p, [r.repoId]: e.target.value }))}
                  onKeyDown={(e) => e.key === 'Enter' && sendInvite(r.repoId)}
                />
                <button
                  className="btn co sm"
                  disabled={busy || !(inviteEmail[r.repoId] || '').includes('@')}
                  onClick={() => sendInvite(r.repoId)}
                >메일 발송</button>
              </div>
            )}
            {inviteError[r.repoId] && (
              <p className="note sm" style={{ marginTop: 6, color: 'var(--coral)' }}>{inviteError[r.repoId]}</p>
            )}
          </div>
          );
        })
      )}
    </main>
  );
}