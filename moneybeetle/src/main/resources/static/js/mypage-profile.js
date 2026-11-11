/* comment.
 * 담당: DBA ② 김예린 (#69, 마이페이지 프로필 요약)
 * fragments/mypage/profile.html 의 #mg-profile 안을 API 로 채운다. 서버가 준 글은 textContent 로만 넣는다.
 * - GET /api/v1/members/me                  (API 13) 프로필·레벨·칭호·경험치·캐시·연속 출석
 * - GET /api/v1/members/{memberId}/profile  (API 18) 수상 내역 (1~3위만 메달)
 * - 설정 탭이 보내는 mg:profile-changed 이벤트(닉네임·프로필 이미지)로 이름·사진을 다시 그린다.
 */
(function () {
  'use strict';
  var root = document.getElementById('mg-profile');
  if (!root || !window.MB) return;

  var MEDAL_POS = ['0% 50%', '50% 50%', '100% 50%'];
  var MEDAL_DISC = ['#F4C542', '#D9DCE3', '#E3A06F'];
  var RANKING_NAMES = { ASSET: '순자산', RETURN: '수익률' };
  var me = null;

  function byId(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }
  function won(n) { return Number(n || 0).toLocaleString('ko-KR'); }

  function drawIdentity() {
    var avatar = byId('mg-profile-avatar');
    avatar.textContent = '';
    if (me.profileImageUrl) {
      var img = el('img');
      img.setAttribute('src', me.profileImageUrl);
      img.setAttribute('alt', '프로필 사진');
      avatar.appendChild(img);
    } else {
      avatar.textContent = me.nickname ? me.nickname.charAt(0) : '?';
    }
    byId('mg-profile-name').textContent = me.nickname || '';
  }

  function drawTags() {
    var tags = byId('mg-profile-tags');
    tags.textContent = '';
    tags.appendChild(el('span', 'tag tag-accent', 'Lv.' + me.level));
    if (me.title) tags.appendChild(el('span', 'tag', me.title));
    tags.appendChild(el('span', 'tag', won(me.exp) + ' XP'));
    var cash = el('span', 'tag');
    var coin = el('img');
    coin.setAttribute('src', '/images/cash-coin.png');
    coin.setAttribute('alt', '캐시');
    cash.appendChild(coin);
    cash.appendChild(document.createTextNode(won(me.cash)));
    tags.appendChild(cash);
    if (me.attendance) tags.appendChild(el('span', 'tag', me.attendance.streak + '일째 출석 중'));
  }

  function drawAwards(awards) {
    var medals = (awards || []).filter(function (award) { return award.rank >= 1 && award.rank <= 3; });
    var box = byId('mg-profile-medals');
    box.textContent = '';
    byId('mg-profile-awards').hidden = !medals.length;
    medals.forEach(function (award) {
      var label = award.seasonName + ' · ' + (RANKING_NAMES[award.rankingType] || award.rankingType) + ' ' + award.rank + '위';
      var medal = el('span', 'mg-medal');
      medal.setAttribute('role', 'img');
      medal.setAttribute('aria-label', label);
      medal.setAttribute('title', label);
      medal.style.setProperty('--pos', MEDAL_POS[award.rank - 1]);
      var number = String(award.seasonName || '').match(/\d+/);
      var disc = el('span', null, number ? number[0] : '');
      disc.style.setProperty('--disc', MEDAL_DISC[award.rank - 1]);
      disc.setAttribute('aria-hidden', 'true');
      medal.appendChild(disc);
      box.appendChild(medal);
    });
  }

  document.addEventListener('mg:profile-changed', function (event) {
    if (!me || !event.detail) return;
    me.nickname = event.detail.nickname;
    me.profileImageUrl = event.detail.profileImageUrl;
    drawIdentity();
  });

  MB.api('/api/v1/members/me').then(function (data) {
    me = data || {};
    byId('mg-profile-mail').textContent = me.email || '';
    drawIdentity();
    drawTags();
    if (me.memberId != null) {
      MB.api('/api/v1/members/' + encodeURIComponent(me.memberId) + '/profile').then(function (profile) {
        drawAwards(profile && profile.awards);
      }, function () { /* 수상 내역을 못 받아도 프로필 요약은 그대로 보인다 */ });
    }
  }, function () {
    byId('mg-profile-name').textContent = '프로필을 불러오지 못했어요.';
  });
})();
