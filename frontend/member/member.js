(() => {
  "use strict";

  const root = document.querySelector(".member-manage");
  if (!root) return;

  const ROLE_LABELS = { OWNER: "방장", MEMBER: "멤버" };

  const pageMessage = document.getElementById("member-page-message");
  const groupName = document.getElementById("member-group-name");
  const detailLink = document.getElementById("member-detail-link");

  const listLoading = document.getElementById("member-list-loading");
  const listError = document.getElementById("member-list-error");
  const listRetry = document.getElementById("member-list-retry");
  const list = document.getElementById("member-list");
  const count = document.getElementById("member-count");

  const inviteForm = document.getElementById("member-invite-form");
  const inviteInput = document.getElementById("member-invite-username");
  const inviteSubmit = document.getElementById("member-invite-submit");
  const inviteStatus = document.getElementById("member-invite-status");

  const leaveButton = document.getElementById("member-leave-button");
  const leaveStatus = document.getElementById("member-leave-status");

  // ---- helpers ----------------------------------------------------------

  function positiveId(value) {
    if (typeof value !== "string" && typeof value !== "number") return null;
    if (typeof value === "string" && !/^\d+$/.test(value.trim())) return null;
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }

  function setMessage(element, message, state = "info") {
    if (!element) return;
    element.textContent = message || "";
    element.dataset.state = state;
    element.hidden = !message;
  }

  // api.js returns body.data, or the whole body when data is null.
  function unwrap(response) {
    if (response && typeof response === "object" && !Array.isArray(response) && "success" in response) {
      if (!response.success) {
        throw new Error(response.message || "요청을 처리하지 못했습니다.");
      }
      return response.data;
    }
    return response;
  }

  function requireApi(method) {
    if (typeof window.api?.[method] !== "function") {
      throw new Error("API를 준비하지 못했습니다. 페이지를 새로고침해 주세요.");
    }
    return window.api[method];
  }

  function errorText(error, fallback) {
    if (error instanceof TypeError) {
      return "서버에 연결하지 못했습니다. 백엔드 서버가 실행 중인지 확인해 주세요.";
    }
    return (error && error.message) || fallback;
  }

  function pageUrl(path, groupId) {
    const url = new URL(path, window.location.href);
    if (groupId !== null) url.searchParams.set("groupId", String(groupId));
    return url.href;
  }

  function formatJoinedAt(value) {
    if (typeof value !== "string" || value.length === 0) return "정보 없음";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date);
  }

  function disableAll() {
    inviteInput.disabled = true;
    inviteSubmit.disabled = true;
    leaveButton.disabled = true;
    listRetry.hidden = true;
  }

  // ---- groupId ------------------------------------------------------------

  const groupId = positiveId(new URLSearchParams(window.location.search).get("groupId"));
  if (groupId === null) {
    listLoading.hidden = true;
    disableAll();
    setMessage(pageMessage, "일기장 번호가 없거나 올바르지 않습니다. 단체 일기장 목록에서 다시 선택해 주세요.", "error");
    return;
  }
  detailLink.href = pageUrl("../group/group-detail.html", groupId);
  const membersPath = `/api/groups/${groupId}/members`;

  // ---- group name (optional decoration) -----------------------------------

  async function loadGroupName() {
    try {
      const group = unwrap(await requireApi("get")(`/api/groups/${groupId}`));
      if (group && typeof group.name === "string" && group.name.length > 0) {
        groupName.textContent = group.name;
        groupName.hidden = false;
      }
    } catch {
      // The member list request reports access errors; the name is only decoration.
    }
  }

  // ---- member list ----------------------------------------------------------

  function renderMembers(members) {
    const fragment = document.createDocumentFragment();
    for (const member of members) {
      if (!member || typeof member !== "object") continue;

      const item = document.createElement("li");
      item.className = "member-item";

      const nickname = document.createElement("span");
      nickname.className = "member-nickname";
      nickname.textContent =
        typeof member.nickname === "string" && member.nickname.length > 0 ? member.nickname : "이름 없음";

      const role = document.createElement("span");
      const roleKey = member.role === "OWNER" ? "OWNER" : "MEMBER";
      role.className = `member-role member-role-${roleKey.toLowerCase()}`;
      role.textContent = ROLE_LABELS[roleKey];

      const joined = document.createElement("span");
      joined.className = "member-joined";
      joined.textContent = `참여 ${formatJoinedAt(member.joinedAt)}`;

      item.append(nickname, role, joined);
      fragment.append(item);
    }
    list.replaceChildren(fragment);
    count.textContent = `${list.childElementCount}명`;
    count.hidden = false;
    list.hidden = false;
  }

  let listPending = false;
  async function loadMembers() {
    if (listPending) return;
    listPending = true;
    listLoading.hidden = false;
    listRetry.hidden = true;
    setMessage(listError, "");

    try {
      const members = unwrap(await requireApi("get")(membersPath));
      if (!Array.isArray(members)) {
        throw new Error("멤버 목록 형식이 올바르지 않습니다.");
      }
      renderMembers(members);
    } catch (error) {
      list.hidden = true;
      count.hidden = true;
      setMessage(listError, errorText(error, "멤버 목록을 불러오지 못했습니다."), "error");
      listRetry.hidden = false;
    } finally {
      listLoading.hidden = true;
      listPending = false;
    }
  }

  listRetry.addEventListener("click", loadMembers);

  // ---- invite -----------------------------------------------------------------

  let invitePending = false;
  inviteInput.addEventListener("input", () => inviteInput.setCustomValidity(""));

  inviteForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (invitePending) return;

    const username = inviteInput.value.trim();
    if (!username) {
      inviteInput.setCustomValidity("초대할 친구의 아이디를 입력해 주세요.");
      inviteInput.reportValidity();
      setMessage(inviteStatus, "초대할 친구의 아이디를 입력해 주세요.", "error");
      return;
    }

    invitePending = true;
    inviteSubmit.disabled = true;
    inviteForm.setAttribute("aria-busy", "true");
    setMessage(inviteStatus, "친구를 초대하고 있습니다.");

    try {
      const member = unwrap(await requireApi("post")(membersPath, { username }));
      const nickname = member && typeof member.nickname === "string" ? member.nickname : username;
      setMessage(inviteStatus, `${nickname}님을 초대했습니다.`, "success");
      inviteInput.value = "";
      await loadMembers();
    } catch (error) {
      setMessage(inviteStatus, errorText(error, "친구를 초대하지 못했습니다."), "error");
    } finally {
      invitePending = false;
      inviteSubmit.disabled = false;
      inviteForm.removeAttribute("aria-busy");
    }
  });

  // ---- leave --------------------------------------------------------------------

  let leavePending = false;
  leaveButton.addEventListener("click", async () => {
    if (leavePending) return;
    const confirmed = window.confirm(
      "정말 이 단체 일기장에서 나가시겠어요?\n나가면 이 일기장을 더 이상 볼 수 없습니다."
    );
    if (!confirmed) return;

    leavePending = true;
    leaveButton.disabled = true;
    setMessage(leaveStatus, "단체 일기장에서 나가는 중입니다.");

    try {
      unwrap(await requireApi("delete")(`${membersPath}/me`));
      setMessage(leaveStatus, "단체 일기장에서 나갔습니다. 목록으로 이동합니다.", "success");
      window.location.assign(pageUrl("../group/group-list.html", null));
    } catch (error) {
      // Never navigate away as if leaving succeeded.
      setMessage(leaveStatus, errorText(error, "단체 일기장에서 나가지 못했습니다."), "error");
      leavePending = false;
      leaveButton.disabled = false;
    }
  });

  loadGroupName();
  loadMembers();
})();
