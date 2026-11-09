// comment.
//   담당 : SCM (Git 규칙·저장소 설정, #186)
//   새 이슈·PR 의 라벨과 담당자(Assignees)를 정하는 규칙. .github/workflows/auto-label.yml 이 부른다
//   - 이미 붙은 라벨·담당자는 지우지 않고, 빠진 것만 더한다
//   - 규칙은 docs/MoneyBeetle_Git규칙.md 4장 "라벨·담당자 자동 지정" 과 같다

// 제목 앞 타입 → 종류 라벨 (refactor·test 처럼 맞는 라벨이 없으면 붙이지 않는다)
const TYPE_LABELS = { feat: 'feat', fix: 'bug', hotfix: 'bug', chore: 'chore', docs: 'docs', release: 'chore' };

// 이슈 본문의 "담당: 이름" → GitHub 아이디 (docs/MoneyBeetle_역할분담.md)
const MEMBERS = {
  '서수영': 'tjtndud2',
  '이재성': 'godgeul-glitch',
  '신아림': 'arim0317-dot',
  '김예린': 'paaaraaaeaaa',
};

const PRIORITY = /^P[123]$/;

// 주석(<!-- -->)은 템플릿 안내문이라 읽지 않는다
function stripComments(text) {
  return (text || '').replace(/<!--[\s\S]*?-->/g, '');
}

// "feat(be): ..." → { type: 'feat', area: 'be' }
function parseTitle(title) {
  const m = /^\s*(\w+)(?:\(([\w-]+)\))?\s*:/.exec(title || '');
  if (!m) return { type: null, area: null };
  return { type: m[1].toLowerCase(), area: m[2] ? m[2].toLowerCase() : null };
}

// "feat/be-12-login-api" → 'be' (영역 뒤에 이슈 번호가 있을 때만. docs/fe-be-split 같은 이름은 영역이 아니다)
function areaFromBranch(branch) {
  const m = /^[\w-]+\/(be|fe)-\d/.exec(branch || '');
  return m ? m[1] : null;
}

// PR 이 맡은 이슈 번호 : 제목의 (#12)·(#4, #27) 와 본문의 Closes/Fixes/Resolves/Refs #12
// 본문 설명에 지나가듯 적힌 #번호(참고 PR 등)는 보지 않는다
function linkedIssueNumbers(title, body) {
  const numbers = [];
  for (const group of (title || '').matchAll(/\(([^)]*#\d+[^)]*)\)/g)) {
    for (const m of group[1].matchAll(/#(\d+)/g)) numbers.push(Number(m[1]));
  }
  const keyword = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?|refs?)\b\s*:?\s+#(\d+)/gi;
  for (const m of stripComments(body).matchAll(keyword)) numbers.push(Number(m[1]));
  return [...new Set(numbers)];
}

// 이슈 본문에서 줄 맨 앞(또는 문장 시작)에 쓴 "담당: 김예린" / "- 담당 : 서수영, 신아림" → 아이디 목록
// "#49 공통 레이아웃 담당: 김예린" 처럼 다른 일의 담당을 설명하는 문장은 담당 지정으로 보지 않는다
function ownersFromBody(body) {
  const text = stripComments(body);
  const found = [];
  for (const m of text.matchAll(/(?:^[\s>*-]*|[.。]\s+)\**담당\**\s*[:：]\s*([^\n.。]+)/gm)) {
    for (const [name, login] of Object.entries(MEMBERS)) {
      if (m[1].includes(name) && !found.includes(login)) found.push(login);
    }
  }
  return found;
}

// 종류·영역 라벨
function typeAndAreaLabels(title, branch) {
  const { type, area } = parseTitle(title);
  const labels = [];
  if (type && TYPE_LABELS[type]) labels.push(TYPE_LABELS[type]);
  const finalArea = area === 'be' || area === 'fe' ? area : areaFromBranch(branch);
  if (finalArea) labels.push(finalArea);
  return labels;
}

/**
 * 이슈에 더할 라벨·담당자
 * @param {{title, body, author, labels: string[], assignees: string[]}} issue
 */
function decideForIssue(issue) {
  const labels = typeAndAreaLabels(issue.title, null).filter((l) => !issue.labels.includes(l));
  let assignees = [];
  if (issue.assignees.length === 0) {
    const owners = ownersFromBody(issue.body);
    assignees = owners.length ? owners : [issue.author];
  }
  return { labels, assignees };
}

/**
 * PR 에 더할 라벨·담당자
 * @param {{title, body, author, branch, labels: string[], assignees: string[]}} pr
 * @param {(n: number) => string[]} labelsOfIssue 연결된 이슈의 라벨 목록 (없으면 [])
 */
function decideForPullRequest(pr, labelsOfIssue) {
  const wanted = typeAndAreaLabels(pr.title, pr.branch);
  // dev → main 릴리스 PR 은 여러 이슈를 묶으므로 우선순위를 붙이지 않는다
  const isRelease = pr.branch === 'dev';
  if (!isRelease && !pr.labels.some((l) => PRIORITY.test(l))) {
    const priorities = linkedIssueNumbers(pr.title, pr.body)
      .flatMap((n) => labelsOfIssue(n))
      .filter((l) => PRIORITY.test(l))
      .sort(); // P1 이 가장 급하므로 가장 앞의 것을 쓴다
    if (priorities.length) wanted.push(priorities[0]);
  }
  const labels = wanted.filter((l) => !pr.labels.includes(l));
  const assignees = pr.assignees.length === 0 ? [pr.author] : [];
  return { labels, assignees };
}

module.exports = {
  decideForIssue,
  decideForPullRequest,
  parseTitle,
  areaFromBranch,
  linkedIssueNumbers,
  ownersFromBody,
};
