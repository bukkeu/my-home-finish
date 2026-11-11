/* comment.
*   담당 : DBA ① 신아림 (#55, 문의 게시판 /user/inquiry)
*   주소가 /user/inquiry/{inquiryId} 이면(답변 알림 링크) 그 문의를 펼쳐서 연다
*  */
(function () {
  var box = document.querySelector('[data-inq-board]');
  if (!box || !window.MB || !window.MBInquiry) return;
  var m = location.pathname.match(/^\/user\/inquiry\/(\d+)\/?$/);
  window.MBInquiry.mount(box, { mode: 'site', openId: m ? Number(m[1]) : null });
})();
