/* app.js — BloodLink Full Application Logic */
"use strict";

/* ── CONFIG ── */
const API_BASE =
  (typeof window !== "undefined" &&
    (window.BLOODLINK_API_BASE || window.API_BASE)) ||
  "https://bloodlink-production-ddab.up.railway.app";

/* ── DB (Session Management) ── */
const DB = {
  _k: (k) => "bl_" + k,
  obj(k, d = {}) {
    try {
      return JSON.parse(localStorage.getItem(this._k(k))) || d;
    } catch {
      return d;
    }
  },
  set(k, v) {
    localStorage.setItem(this._k(k), JSON.stringify(v));
  },
  del(k) {
    localStorage.removeItem(this._k(k));
  },
};

/* ── AUTHENTICATED API HELPER ── */
async function apiFetch(url, options = {}) {
  const opts = { ...options };
  opts.headers = { ...(options.headers || {}) };

  const isAuthEndpoint =
    typeof url === "string" &&
    (url.includes("/api/auth/login") || url.includes("/api/auth/signup"));

  const session = DB.obj("session", null);
  if (
    !isAuthEndpoint &&
    session &&
    session.token &&
    typeof url === "string" &&
    url.includes("/api/")
  ) {
    opts.headers["Authorization"] = `Bearer ${session.token}`;
  }

  const res = await fetch(url, opts);

  if (res.status === 401 && !isAuthEndpoint) {
    DB.del("session");
    CU = null;
    if (typeof pollTimer !== "undefined" && pollTimer) clearInterval(pollTimer);
    const appScr = typeof $ === "function" ? $("scr-app") : null;
    if (appScr && appScr.style.display !== "none") {
      appScr.style.display = "none";
      const landingScr = $("scr-landing");
      if (landingScr) landingScr.style.display = "block";
      if (typeof initLanding === "function") initLanding();
      if (typeof toast === "function")
        toast("Session expired. Please log in again.", "err");
    }
  }

  return res;
}

/* ── STATE ── */
let CU = null,
  curRole = "donor",
  signupRep = null,
  repUrl = null,
  selBg = "",
  selR = 5,
  selHR = 5;

/* ── UTILS ── */
const $ = (id) => document.getElementById(id);
const esc = (s) =>
  String(s || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
const gv = (id) => $(id)?.value?.trim() || "";
const isValidEmail = (email) =>
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test((email || "").trim());
const isValidPhone = (phone) => /^[6-9]\d{9}$/.test((phone || "").trim());
const isValidPassword = (pass) =>
  /^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(pass || "");
const isValidName = (name) => {
  const t = (name || "").trim();
  return t.length > 0 && !/^\d+$/.test(t);
};
const sv = (id, v) => {
  const e = $(id);
  if (e) e.value = v ?? "";
};
const setE = (id, m) => {
  const e = $(id + "-e");
  if (e) {
    e.textContent = m;
    e.classList.add("show");
  }
  $(id)?.classList.add("err");
};
const clrE = (id) => {
  const e = $(id + "-e");
  if (e) {
    e.textContent = "";
    e.classList.remove("show");
  }
  $(id)?.classList.remove("err");
};
const openModal = (id) => {
  $(id)?.classList.add("open");
};
const closeModal = (id) => {
  $(id)?.classList.remove("open");
};

function toast(msg, type = "info") {
  const d = document.createElement("div");
  d.className = "toast " + type;
  const icon = { ok: "✅", err: "❌", info: "ℹ️" }[type] || "💬";
  d.innerHTML = `<span>${icon}</span><span>${esc(msg)}</span>`;
  $("toast-wrap").appendChild(d);
  setTimeout(() => {
    d.style.cssText = "opacity:0;transform:translateX(20px);transition:.3s";
    setTimeout(() => d.remove(), 300);
  }, 3000);
}

const SVG_EYE_SLASH = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>';
const SVG_EYE = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>';

function togglePass(id, btn) {
  const inp = $(id);
  if (!inp) return;
  const isHidden = inp.type === "password";
  inp.type = isHidden ? "text" : "password";
  const button = btn || (inp.parentNode && inp.parentNode.querySelector(".pass-toggle-btn"));
  if (button) {
    button.innerHTML = isHidden ? SVG_EYE : SVG_EYE_SLASH;
    const label = isHidden ? "Hide password" : "Show password";
    button.setAttribute("aria-label", label);
    button.setAttribute("title", label);
  }
}

function parseServerDate(value) {
  if (!value) return null;
  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value;
  }
  if (typeof value === "number") {
    const d = new Date(value);
    return isNaN(d.getTime()) ? null : d;
  }
  if (typeof value === "string") {
    const v = value.trim();
    if (!v) return null;
    // Pure date (YYYY-MM-DD) -> preserve calendar date without UTC shift
    if (/^\d{4}-\d{2}-\d{2}$/.test(v)) {
      const d = new Date(v + "T12:00:00");
      return isNaN(d.getTime()) ? null : d;
    }
    // Datetime that already includes timezone info (e.g., ends in 'Z' or +/-offset)
    if (/[zZ]$|[+-]\d{2}(:?\d{2})?$/.test(v)) {
      const d = new Date(v);
      return isNaN(d.getTime()) ? null : d;
    }
    // Server LocalDateTime without timezone (e.g., 2026-09-17T06:30:00 or 2026-09-17 06:30:00) -> treat as UTC
    if (/^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(v)) {
      const d = new Date(v.replace(" ", "T") + "Z");
      return isNaN(d.getTime()) ? null : d;
    }
    const d = new Date(v);
    return isNaN(d.getTime()) ? null : d;
  }
  return null;
}

function fmt(s) {
  if (!s) return "—";
  try {
    const d = parseServerDate(s);
    if (!d || isNaN(d.getTime())) return s;
    return d.toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  } catch {
    return s;
  }
}
function ago(s) {
  if (!s) return "";
  const serverD = parseServerDate(s);
  if (!serverD || isNaN(serverD.getTime())) return "";
  const d = (Date.now() - serverD.getTime()) / 1000;
  if (d < 60) return "just now";
  if (d < 3600) return ~~(d / 60) + "m ago";
  if (d < 86400) return ~~(d / 3600) + "h ago";
  if (d < 604800) return ~~(d / 86400) + "d ago";
  return fmt(s);
}
function dist(la1, lo1, la2, lo2) {
  if (la1 == null || lo1 == null || la2 == null || lo2 == null) return null;
  const R = 6371,
    d1 = ((la2 - la1) * Math.PI) / 180,
    d2 = ((lo2 - lo1) * Math.PI) / 180;
  const a =
    Math.sin(d1 / 2) ** 2 +
    Math.cos((la1 * Math.PI) / 180) *
      Math.cos((la2 * Math.PI) / 180) *
      Math.sin(d2 / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
function elig(d, nextEligDate) {
  if (!d)
    return {
      ok: true,
      msg: "No donation history — you are eligible!",
      days: 0,
      left: 0,
      lastDonation: "",
      nextEligible: "",
    };
  try {
    const dDate =
      typeof d === "string" && d.includes("T")
        ? new Date(d)
        : new Date(d + "T00:00:00");
    let nextDate;
    if (nextEligDate) {
      nextDate =
        typeof nextEligDate === "string" && nextEligDate.includes("T")
          ? new Date(nextEligDate)
          : new Date(nextEligDate + "T00:00:00");
    } else {
      nextDate = new Date(dDate.getTime() + 90 * 86400000);
    }
    const now = new Date();
    const todayZero = new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate(),
    );
    const dZero = new Date(
      dDate.getFullYear(),
      dDate.getMonth(),
      dDate.getDate(),
    );
    const nextZero = new Date(
      nextDate.getFullYear(),
      nextDate.getMonth(),
      nextDate.getDate(),
    );

    const daysSince = Math.round((todayZero - dZero) / 86400000);
    const leftDays = Math.round((nextZero - todayZero) / 86400000);

    const isElig = leftDays <= 0;
    const nextStr = nextZero.toISOString().split("T")[0];

    if (isElig) {
      return {
        ok: true,
        msg: `Eligible! ${daysSince} days since last donation.`,
        days: daysSince,
        left: 0,
        lastDonation: d,
        nextEligible: nextStr,
      };
    } else {
      return {
        ok: false,
        msg: `You donated on ${fmt(d)}. You can donate again from ${fmt(nextStr)} (${leftDays} day${leftDays === 1 ? "" : "s"} remaining).`,
        days: daysSince,
        left: leftDays,
        lastDonation: d,
        nextEligible: nextStr,
      };
    }
  } catch {
    return {
      ok: true,
      msg: "Eligibility check completed.",
      days: 0,
      left: 0,
      lastDonation: d,
      nextEligible: "",
    };
  }
}
function bgClass(g) {
  return "bg-" + (g || "").replace("+", "pos").replace("-", "neg");
}
function hashPass(p) {
  let h = 0;
  for (const c of p) h = (Math.imul(31, h) + c.charCodeAt(0)) | 0;
  return h.toString(36);
}

const BB_GROUPS = ["A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"];
function emptyBloodStock() {
  const stock = {};
  BB_GROUPS.forEach((g) => {
    stock[g] = 0;
  });
  return stock;
}
function emptyLastUpdated() {
  const lu = {};
  BB_GROUPS.forEach((g) => {
    lu[g] = null;
  });
  return lu;
}
let bloodBankInventoriesCache = {};

/** Fetches inventory from Spring Boot + MySQL backend and caches it */
function fetchBloodBankInventory(bloodBankId) {
  if (!bloodBankId) {
    return Promise.resolve({
      stock: emptyBloodStock(),
      reservedStock: emptyBloodStock(),
      lastUpdated: emptyLastUpdated(),
    });
  }
  return apiFetch(
    `${API_BASE}/api/bloodbanks/${encodeURIComponent(bloodBankId)}/inventory`,
  )
    .then(async (res) => {
      if (!res.ok) {
        return (
          bloodBankInventoriesCache[bloodBankId] || {
            stock: emptyBloodStock(),
            reservedStock: emptyBloodStock(),
            lastUpdated: emptyLastUpdated(),
          }
        );
      }
      const data = await res.json();
      const stock = { ...emptyBloodStock(), ...(data.stock || {}) };
      const reservedStock = {
        ...emptyBloodStock(),
        ...(data.reservedStock || {}),
      };
      const lastUpdated = {
        ...emptyLastUpdated(),
        ...(data.lastUpdated || {}),
      };
      bloodBankInventoriesCache[bloodBankId] = {
        stock,
        reservedStock,
        lastUpdated,
      };
      return bloodBankInventoriesCache[bloodBankId];
    })
    .catch(() => {
      return (
        bloodBankInventoriesCache[bloodBankId] || {
          stock: emptyBloodStock(),
          reservedStock: emptyBloodStock(),
          lastUpdated: emptyLastUpdated(),
        }
      );
    });
}

function ensureBloodBankInventory(bloodBankId) {
  if (!bloodBankId) return false;
  fetchBloodBankInventory(bloodBankId);
  return true;
}

function getBloodBankInventory(bloodBankId) {
  if (bloodBankInventoriesCache[bloodBankId]) {
    return bloodBankInventoriesCache[bloodBankId];
  }
  return {
    stock: emptyBloodStock(),
    reservedStock: emptyBloodStock(),
    lastUpdated: emptyLastUpdated(),
  };
}

function saveBloodBankInventory(bloodBankId, stock, lastUpdated) {
  if (!bloodBankId) return Promise.reject(new Error("No blood bank ID"));
  const payload = {
    stock: stock || emptyBloodStock(),
    lastUpdated: lastUpdated || emptyLastUpdated(),
  };
  return apiFetch(
    `${API_BASE}/api/bloodbanks/${encodeURIComponent(bloodBankId)}/inventory`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  ).then(async (res) => {
    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      const err = data.error || "Failed to update inventory in database";
      toast(err, "err");
      throw new Error(err);
    }
    const data = await res.json();
    const updatedStock = { ...emptyBloodStock(), ...(data.stock || {}) };
    const updatedReservedStock = {
      ...emptyBloodStock(),
      ...(data.reservedStock || {}),
    };
    const updatedLastUpdated = {
      ...emptyLastUpdated(),
      ...(data.lastUpdated || {}),
    };
    bloodBankInventoriesCache[bloodBankId] = {
      stock: updatedStock,
      reservedStock: updatedReservedStock,
      lastUpdated: updatedLastUpdated,
    };
    return bloodBankInventoriesCache[bloodBankId];
  });
}
function fmtDateTime(isoStr) {
  if (!isoStr) return "Not updated yet";
  try {
    const d = parseServerDate(isoStr);
    if (!d || isNaN(d.getTime())) return "Not updated yet";
    const datePart = d.toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
    const timePart = d.toLocaleTimeString("en-IN", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: true,
    });
    return `${datePart}, ${timePart}`;
  } catch {
    return "Not updated yet";
  }
}
function calcStockStatus(units) {
  if (units <= 0) return { label: "No Stock", badgeClass: "grey" };
  if (units <= 5) return { label: "Low Stock", badgeClass: "amber" };
  return { label: "Available", badgeClass: "green" };
}
function roleKind(u) {
  const r = (u || CU || {}).role;
  if (r === "bloodbank") return "bloodbank";
  if (r === "hospital") return "hospital";
  return "donor";
}

/* ── LEGACY LOCALSTORAGE CLEANUP ── */
function cleanupLegacyStorage() {
  try {
    localStorage.removeItem("bl_users");
    localStorage.removeItem("bl_requests");
    localStorage.removeItem("bl_history");
    localStorage.removeItem("bl_inventory");
    localStorage.removeItem("bl_reviews");
    localStorage.removeItem("bl_seeded");
  } catch {}
}

/* ── THEME ── */
const toggleTheme = () => {
  const t =
    document.documentElement.getAttribute("data-theme") === "dark"
      ? "light"
      : "dark";
  document.documentElement.setAttribute("data-theme", t);
  localStorage.setItem("bl_theme", t);
};
(() => {
  document.documentElement.setAttribute(
    "data-theme",
    localStorage.getItem("bl_theme") || "light",
  );
})();

/* ── LANDING ── */
function initLanding() {
  updateStats();
  renderLandingReviews();
  renderLandingTicker();
}

function updateStats() {
  const d = $("l-stat-donors");
  const h = $("l-stat-hospitals");
  const b = $("l-stat-bloodbanks");
  const r = $("l-stat-requests");

  apiFetch(`${API_BASE}/api/users/stats`)
    .then((res) => res.json())
    .then((data) => {
      if (d && typeof data.donors === "number")
        d.textContent = data.donors.toLocaleString();
      if (h && typeof data.hospitals === "number")
        h.textContent = data.hospitals.toLocaleString();
      if (b && typeof data.bloodbanks === "number")
        b.textContent = data.bloodbanks.toLocaleString();
    })
    .catch(() => {});

  apiFetch(`${API_BASE}/api/requests/count`)
    .then((res) => res.json())
    .then((data) => {
      if (r && typeof data.count === "number") {
        r.textContent = data.count.toLocaleString();
      }
    })
    .catch(() => {
      if (r) r.textContent = "0";
    });
}

function renderLandingTicker() {
  const t = $("ticker-inner");
  if (!t) return;

  const buildTicker = (donorCount = 0, reqCount = 0, invUpdateCount = 0) => {
    const items = [];

    // Real donor joins
    for (let i = 0; i < donorCount; i++) {
      items.push("🩸 A registered donor joined BloodLink");
    }

    // Real hospital requests
    for (let i = 0; i < reqCount; i++) {
      items.push("🏥 A hospital created a blood request");
    }

    // Real blood bank inventory updates
    for (let i = 0; i < invUpdateCount; i++) {
      items.push("🏦 A blood bank updated its inventory");
    }

    if (items.length === 0) {
      t.innerHTML = `<span class="ticker-item"><span class="ticker-dot"></span>No recent activity yet.</span>`;
      return;
    }

    const repeatList =
      items.length < 5
        ? [...items, ...items, ...items, ...items]
        : [...items, ...items];
    t.innerHTML = repeatList
      .map(
        (item) =>
          `<span class="ticker-item"><span class="ticker-dot"></span>${item}</span>`,
      )
      .join("");
  };

  Promise.all([
    apiFetch(`${API_BASE}/api/users/stats`)
      .then((res) => res.json())
      .catch(() => ({ donors: 0 })),
    apiFetch(`${API_BASE}/api/requests/count`)
      .then((res) => res.json())
      .catch(() => ({ count: 0 })),
    apiFetch(`${API_BASE}/api/bloodbanks/inventory/updates-count`)
      .then((res) => res.json())
      .catch(() => ({ count: 0 })),
  ]).then(([userData, reqData, invData]) => {
    const donorCount =
      typeof userData.donors === "number" ? userData.donors : 0;
    const reqCount = typeof reqData.count === "number" ? reqData.count : 0;
    const invCount = typeof invData.count === "number" ? invData.count : 0;
    buildTicker(donorCount, reqCount, invCount);
  });
}

async function renderLandingReviews() {
  const container = $("testi-grid");
  if (!container) return;

  try {
    const res = await apiFetch(`${API_BASE}/api/reviews`);
    const reviews = res.ok ? await res.json() : [];
    if (!reviews.length) {
      container.innerHTML = `
        <div class="empty" style="grid-column: 1 / -1; text-align: center; padding: 40px 20px;">
          <div style="font-size:36px;margin-bottom:8px">💬</div>
          <div style="font-size:15px;color:var(--text-m);font-weight:600">No reviews yet. Be the first to share your experience.</div>
        </div>`;
      return;
    }

    const roleLabelMap = {
      donor: "Donor",
      hospital: "Hospital",
      bloodbank: "Blood Bank",
    };

    let html = "";
    reviews.forEach((rev) => {
      const stars =
        "★".repeat(Math.max(1, Math.min(5, rev.rating || 5))) +
        "☆".repeat(5 - Math.max(1, Math.min(5, rev.rating || 5)));
      const roleText = roleLabelMap[rev.role] || "User";
      const initial = (rev.name || "?")[0].toUpperCase();
      const avBg =
        rev.role === "donor"
          ? "var(--crimson)"
          : rev.role === "hospital"
            ? "var(--sapphire)"
            : "var(--emerald)";

      html += `
        <div class="testi">
          <div style="color:#F59E0B;font-size:16px;margin-bottom:8px;letter-spacing:2px">${stars}</div>
          <p class="tq">"${esc(rev.text)}"</p>
          <div class="ta">
            <div class="tav" style="background:${avBg}">${initial}</div>
            <div>
              <b>${esc(rev.name)}</b>
              <span>${roleText}</span>
            </div>
          </div>
        </div>`;
    });

    container.innerHTML = html;
  } catch (err) {
    container.innerHTML = `
      <div class="empty" style="grid-column: 1 / -1; text-align: center; padding: 40px 20px;">
        <div style="font-size:36px;margin-bottom:8px">💬</div>
        <div style="font-size:15px;color:var(--text-m);font-weight:600">No reviews yet. Be the first to share your experience.</div>
      </div>`;
  }
}

let curRatingVal = 5;
let pendingDeleteReviewId = null;

function setRevRating(val) {
  curRatingVal = val;
  const starsContainer = $("rev-rating-stars");
  if (starsContainer) {
    const stars = starsContainer.querySelectorAll("span");
    stars.forEach((star, idx) => {
      star.style.color = idx < val ? "#F59E0B" : "var(--border)";
    });
  }
}

async function openReviewModal(isEdit = false) {
  if (!CU) {
    toast("Please sign in to write a review", "info");
    return;
  }
  clrE("rev-text");
  const modalTitle = $("rev-modal-title");
  const submitBtn = $("rev-submit-btn");

  try {
    const res = await apiFetch(`${API_BASE}/api/reviews/user/${CU.id}`);
    if (res.ok) {
      const existing = await res.json();
      setRevRating(existing.rating || 5);
      sv("rev-text", existing.text || "");
      if (modalTitle) modalTitle.textContent = "✏️ Edit Your Review";
      if (submitBtn) submitBtn.textContent = "Update Review ✓";
    } else {
      setRevRating(5);
      sv("rev-text", "");
      if (modalTitle) modalTitle.textContent = "⭐ Share Your Review";
      if (submitBtn) submitBtn.textContent = "Submit Review ✓";
    }
  } catch (e) {
    setRevRating(5);
    sv("rev-text", "");
    if (modalTitle) modalTitle.textContent = "⭐ Share Your Review";
    if (submitBtn) submitBtn.textContent = "Submit Review ✓";
  }
  openModal("modal-review");
}

async function doSubmitReview() {
  if (!CU) return;
  clrE("rev-text");
  const text = gv("rev-text").trim();
  if (!text) {
    setE("rev-text", "Please write a few words about your experience");
    return;
  }

  try {
    const res = await apiFetch(`${API_BASE}/api/reviews`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: CU.id,
        rating: curRatingVal,
        text: text,
      }),
    });

    if (res.ok) {
      closeModal("modal-review");
      toast("Thank you for your review! ⭐", "ok");
      renderReviewsHub();
      renderLandingReviews();
    } else {
      const data = await res.json().catch(() => ({}));
      toast(data.error || "Failed to submit review", "err");
    }
  } catch (err) {
    toast("Failed to submit review. Please try again.", "err");
  }
}

function confirmDeleteReview(reviewId) {
  pendingDeleteReviewId = reviewId;
  openModal("modal-delete-review");
}

async function doDeleteReview() {
  if (!CU || !pendingDeleteReviewId) return;
  try {
    const res = await apiFetch(
      `${API_BASE}/api/reviews/${encodeURIComponent(pendingDeleteReviewId)}?userId=${encodeURIComponent(CU.id)}`,
      {
        method: "DELETE",
      },
    );
    if (res.ok) {
      closeModal("modal-delete-review");
      pendingDeleteReviewId = null;
      toast("Review deleted successfully 🗑️", "ok");
      renderReviewsHub();
      renderLandingReviews();
    } else {
      const data = await res.json().catch(() => ({}));
      toast(data.error || "Failed to delete review", "err");
    }
  } catch (err) {
    toast("Failed to delete review. Please try again.", "err");
  }
}

async function renderReviewsHub() {
  const kind = roleKind();
  const prefix = kind === "donor" ? "d-" : kind === "bloodbank" ? "b-" : "h-";
  const container = $(prefix + "reviews-content");
  if (!container) return;

  container.innerHTML = `
    <div class="sec-hdr">
      <div>
        <div class="sec-title">⭐ Reviews & Stories</div>
        <div class="sec-sub">Share your feedback and explore experiences from donors, hospitals, and blood banks</div>
      </div>
    </div>
    <div style="text-align:center;padding:40px 20px;">
      <div class="spinner"></div>
      <div style="margin-top:12px;color:var(--text-m);font-size:13px">Loading reviews...</div>
    </div>
  `;

  try {
    const [allRes, myRes] = await Promise.all([
      apiFetch(`${API_BASE}/api/reviews`).catch(() => null),
      CU
        ? apiFetch(`${API_BASE}/api/reviews/user/${CU.id}`).catch(() => null)
        : Promise.resolve(null),
    ]);

    const allReviews = allRes && allRes.ok ? await allRes.json() : [];
    const myReview = myRes && myRes.ok ? await myRes.json() : null;

    const roleLabelMap = {
      donor: "Donor",
      hospital: "Hospital",
      bloodbank: "Blood Bank",
    };
    const roleIconMap = {
      donor: "🩸",
      hospital: "🏥",
      bloodbank: "🏦",
    };
    const roleBadgeClassMap = {
      donor: "red",
      hospital: "blue",
      bloodbank: "green",
    };

    let mySectionHtml = "";
    if (myReview) {
      const stars =
        "★".repeat(Math.max(1, Math.min(5, myReview.rating || 5))) +
        "☆".repeat(5 - Math.max(1, Math.min(5, myReview.rating || 5)));
      const roleLabel = roleLabelMap[myReview.role] || "User";
      const roleIcon = roleIconMap[myReview.role] || "👤";
      const badgeClass = roleBadgeClassMap[myReview.role] || "blue";

      let dateStr = "";
      if (myReview.createdAt) {
        try {
          const d = parseServerDate(myReview.createdAt);
          dateStr = d
            ? d.toLocaleDateString(undefined, {
                year: "numeric",
                month: "short",
                day: "numeric",
              })
            : myReview.createdAt;
        } catch {
          dateStr = myReview.createdAt;
        }
      }

      mySectionHtml = `
        <div class="card" style="border: 1.5px solid var(--border-focus); margin-bottom: 24px; background: linear-gradient(to bottom, var(--card), rgba(0,0,0,0.02));">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:12px;flex-wrap:wrap;gap:10px">
            <div style="display:flex;align-items:center;gap:10px">
              <span class="badge ${badgeClass}">${roleIcon} You (${roleLabel})</span>
              <span style="color:#F59E0B;font-size:18px;letter-spacing:2px">${stars}</span>
            </div>
            ${dateStr ? `<span style="font-size:12px;color:var(--text-m);font-weight:600">${dateStr}</span>` : ""}
          </div>
          <p class="tq" style="margin-bottom:16px;font-size:15.5px">"${esc(myReview.text)}"</p>
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;padding-top:12px;border-top:1px solid var(--border)">
            <div class="ta" style="gap:10px">
              <div class="tav" style="background:${myReview.role === "donor" ? "var(--crimson)" : myReview.role === "hospital" ? "var(--sapphire)" : "var(--emerald)"}">
                ${(myReview.name || "?")[0].toUpperCase()}
              </div>
              <div>
                <b>${esc(myReview.name)}</b>
                <span>Your published review</span>
              </div>
            </div>
            <div style="display:flex;gap:8px">
              <button class="btn blue sm" onclick="openReviewModal(true)">✏️ Edit Review</button>
              <button class="btn ghost red sm" onclick="confirmDeleteReview('${myReview.id}')">🗑️ Delete Review</button>
            </div>
          </div>
        </div>
      `;
    } else {
      mySectionHtml = `
        <div class="card" style="margin-bottom: 24px; text-align: center; padding: 28px 20px;">
          <div style="font-size: 32px; margin-bottom: 8px">💬</div>
          <div style="font-weight: 700; font-size: 16px; margin-bottom: 6px">You haven't submitted a review yet</div>
          <p style="color: var(--text-m); font-size: 13.5px; max-width: 500px; margin: 0 auto 18px auto">
            Your feedback and story help other donors, hospitals, and blood banks connect and save lives together.
          </p>
          <button class="btn red" onclick="openReviewModal(false)">
            ⭐ Share Your Experience
          </button>
        </div>
      `;
    }

    // Community reviews (all reviews)
    let commHtml = "";
    if (!allReviews.length) {
      commHtml = `
        <div class="empty" style="grid-column: 1 / -1; text-align: center; padding: 40px 20px;">
          <div style="font-size:36px;margin-bottom:8px">💬</div>
          <div style="font-size:15px;color:var(--text-m);font-weight:600">No community reviews yet. Be the first to share your experience!</div>
        </div>
      `;
    } else {
      commHtml = `<div class="testi-grid">`;
      allReviews.forEach((rev) => {
        const stars =
          "★".repeat(Math.max(1, Math.min(5, rev.rating || 5))) +
          "☆".repeat(5 - Math.max(1, Math.min(5, rev.rating || 5)));
        const roleText = roleLabelMap[rev.role] || "User";
        const roleIcon = roleIconMap[rev.role] || "👤";
        const badgeClass = roleBadgeClassMap[rev.role] || "blue";
        const initial = (rev.name || "?")[0].toUpperCase();
        const avBg =
          rev.role === "donor"
            ? "var(--crimson)"
            : rev.role === "hospital"
              ? "var(--sapphire)"
              : "var(--emerald)";

        const isMe = CU && rev.userId === CU.id;
        let dateStr = "";
        if (rev.createdAt) {
          try {
            const d = parseServerDate(rev.createdAt);
            dateStr = d
              ? d.toLocaleDateString(undefined, {
                  year: "numeric",
                  month: "short",
                  day: "numeric",
                })
              : rev.createdAt;
          } catch {
            dateStr = rev.createdAt;
          }
        }

        commHtml += `
          <div class="testi" style="position:relative;${isMe ? "border: 1.5px solid var(--border-focus);" : ""}">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
              <div style="color:#F59E0B;font-size:16px;letter-spacing:2px">${stars}</div>
              <span class="badge ${badgeClass}">${roleIcon} ${isMe ? "You (" + roleText + ")" : roleText}</span>
            </div>
            <p class="tq">"${esc(rev.text)}"</p>
            <div class="ta" style="justify-content:space-between">
              <div style="display:flex;align-items:center;gap:12px">
                <div class="tav" style="background:${avBg}">${initial}</div>
                <div>
                  <b>${esc(rev.name)}</b>
                  <span>${roleText}</span>
                </div>
              </div>
              ${dateStr ? `<span style="font-size:11px;color:var(--text-m);font-weight:600">${dateStr}</span>` : ""}
            </div>
          </div>
        `;
      });
      commHtml += `</div>`;
    }

    container.innerHTML = `
      <div class="sec-hdr">
        <div>
          <div class="sec-title">⭐ Reviews & Stories</div>
          <div class="sec-sub">Share your feedback and explore experiences from donors, hospitals, and blood banks</div>
        </div>
      </div>

      <div class="ctitle" style="font-size: 16px; margin-bottom: 12px">
        <span>👤 My Review</span>
      </div>
      ${mySectionHtml}

      <div class="ctitle" style="font-size: 16px; margin-top: 28px; margin-bottom: 14px; display:flex; justify-content:space-between; align-items:center">
        <span>💬 Community Stories (${allReviews.length})</span>
      </div>
      ${commHtml}
    `;
  } catch (err) {
    container.innerHTML = `
      <div class="sec-hdr">
        <div>
          <div class="sec-title">⭐ Reviews & Stories</div>
          <div class="sec-sub">Share your feedback and explore experiences from donors, hospitals, and blood banks</div>
        </div>
      </div>
      <div class="empty" style="text-align: center; padding: 40px 20px;">
        <div style="font-size:36px;margin-bottom:8px">⚠️</div>
        <div style="font-size:15px;color:var(--text-m);font-weight:600">Failed to load reviews. Please try again later.</div>
      </div>
    `;
  }
}

/* ── AUTH ── */
function openAuth(role, tab) {
  curRole = role;
  $("scr-landing").style.display = "none";
  $("scr-auth").style.display = "flex";
  const p = $("auth-panel");
  p.classList.remove("hosp", "bank");
  if (role === "hospital") {
    p.classList.add("hosp");
    $("ap-quote").textContent =
      '"Saving lives starts with finding the right donor fast."';
    $("ap-sub").textContent =
      "Register your hospital to access thousands of verified donors near you.";
  } else if (role === "bloodbank") {
    p.classList.add("bank");
    $("ap-quote").textContent =
      '"Reliable inventory is the first step to saving lives."';
    $("ap-sub").textContent =
      "Register your blood bank to record and update blood group inventory.";
  } else {
    $("ap-quote").textContent = '"Every drop counts. Every donor is a hero."';
    $("ap-sub").textContent =
      "Join thousands saving lives through smart geo-based blood matching.";
  }
  switchAuthTab(tab || "login");
}
function backToLanding() {
  var auth = $("scr-auth"),
    land = $("scr-landing");
  auth.style.transition = "opacity 0.25s ease";
  auth.style.opacity = "0";
  setTimeout(function () {
    auth.style.display = "none";
    auth.style.opacity = "";
    auth.style.transition = "";
    land.style.display = "block";
    land.style.opacity = "0";
    land.style.transition = "opacity 0.3s ease";
    requestAnimationFrame(function () {
      requestAnimationFrame(function () {
        land.style.opacity = "1";
        setTimeout(function () {
          land.style.transition = "";
          land.style.opacity = "";
        }, 350);
      });
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, 260);
}
function switchAuthTab(tab) {
  $("auth-login-form").style.display = "none";
  $("auth-signup-donor").style.display = "none";
  $("auth-signup-hospital").style.display = "none";
  $("auth-signup-bloodbank").style.display = "none";
  ["atab-login", "atab-signup"].forEach((id) =>
    $(id).classList.remove("active"),
  );
  if (tab === "login") {
    $("atab-login").classList.add("active");
    $("auth-login-form").style.display = "block";
    $("auth-login-title").textContent =
      curRole === "hospital"
        ? "Hospital Login"
        : curRole === "bloodbank"
          ? "Blood Bank Login"
          : "Welcome back!";
    $("auth-login-sub").textContent =
      curRole === "hospital"
        ? "Access your hospital dashboard"
        : curRole === "bloodbank"
          ? "Access your blood bank dashboard"
          : "Login to your account";
  } else {
    $("atab-signup").classList.add("active");
    const formId =
      curRole === "hospital"
        ? "auth-signup-hospital"
        : curRole === "bloodbank"
          ? "auth-signup-bloodbank"
          : "auth-signup-donor";
    $(formId).style.display = "block";
  }
}

/* ── LOGIN ── */
function doLogin() {
  ["l-email", "l-pass"].forEach(clrE);
  const email = gv("l-email"),
    pass = gv("l-pass");
  let ok = true;
  if (!email || !isValidEmail(email)) {
    setE("l-email", "Valid email address required");
    ok = false;
  }
  if (!pass || !pass.trim()) {
    setE("l-pass", "Password required");
    ok = false;
  }
  if (!ok) return;

  apiFetch(`${API_BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: email.trim().toLowerCase(), password: pass }),
  })
    .then(async (res) => {
      const data = await res.json();
      if (!res.ok) {
        setE("l-pass", data.error || "Invalid email or password");
        return;
      }
      login(data);
    })
    .catch(() => {
      setE("l-pass", "Unable to connect to server. Please try again.");
    });
}

/* ── SIGNUP FORM RESET ── */
function clearSignupForms() {
  const donorFields = [
    "sd-name",
    "sd-age",
    "sd-gender",
    "sd-email",
    "sd-phone",
    "sd-bg",
    "sd-city",
    "sd-state",
    "sd-lat",
    "sd-lng",
    "sd-pass",
    "sd-pass2",
  ];
  const hospitalFields = [
    "sh-name",
    "sh-reg",
    "sh-phone",
    "sh-email",
    "sh-city",
    "sh-state",
    "sh-lat",
    "sh-lng",
    "sh-pass",
    "sh-pass2",
  ];
  const bloodbankFields = [
    "bb-name",
    "bb-reg",
    "bb-phone",
    "bb-email",
    "bb-city",
    "bb-state",
    "bb-hours",
    "bb-lat",
    "bb-lng",
    "bb-pass",
    "bb-pass2",
  ];

  const allFields = [...donorFields, ...hospitalFields, ...bloodbankFields];

  allFields.forEach((id) => {
    const el = $(id);
    if (el) el.value = "";
    clrE(id);
  });

  // Reset file input & dropzone state
  signupRep = null;
  const sdFile = $("sd-file");
  if (sdFile) sdFile.value = "";
  const sdDrop = $("sd-drop");
  if (sdDrop) {
    sdDrop.className = "drop-zone";
    sdDrop.innerHTML =
      "<div>📄</div><b>Upload blood report</b><small>PDF or Image · Max 5MB</small>";
  }

  // Reset location statuses & detect buttons
  ["sd-loc-status", "sh-loc-status", "bb-loc-status"].forEach((id) => {
    const el = $(id);
    if (el) el.textContent = "Detect real GPS coordinates";
  });
  ["sd-loc-box", "sh-loc-box", "bb-loc-box"].forEach((id) => {
    const box = $(id);
    if (box) {
      const btn = box.querySelector("button");
      if (btn) {
        btn.textContent = "Detect";
        btn.disabled = false;
      }
    }
  });
}

/* ── SIGNUP ── */
function doSignup(role) {
  if (role === "donor") {
    const ids = [
      "sd-name",
      "sd-age",
      "sd-gender",
      "sd-email",
      "sd-phone",
      "sd-bg",
      "sd-city",
      "sd-pass",
      "sd-pass2",
      "sd-lat",
      "sd-lng",
    ];
    ids.forEach(clrE);
    const name = gv("sd-name"),
      rawAge = gv("sd-age"),
      age = Number(rawAge),
      gender = gv("sd-gender"),
      email = gv("sd-email"),
      phone = gv("sd-phone"),
      bg = gv("sd-bg"),
      city = gv("sd-city"),
      pass = gv("sd-pass"),
      pass2 = gv("sd-pass2");
    let ok = true;
    if (!name || !isValidName(name)) {
      setE("sd-name", "Valid name required (cannot be numbers only)");
      ok = false;
    }
    if (
      !rawAge ||
      isNaN(age) ||
      !Number.isInteger(age) ||
      age < 18 ||
      age > 65
    ) {
      setE("sd-age", "Donor age must be an integer between 18 and 65");
      ok = false;
    }
    if (!gender || !["MALE", "FEMALE"].includes(gender)) {
      setE("sd-gender", "Select your gender");
      ok = false;
    }
    if (!email || !isValidEmail(email)) {
      setE("sd-email", "Valid email address required");
      ok = false;
    }
    if (!phone || !isValidPhone(phone)) {
      setE(
        "sd-phone",
        "Must be a valid 10-digit Indian mobile number (starting with 6-9)",
      );
      ok = false;
    }
    if (!bg || !BB_GROUPS.includes(bg)) {
      setE("sd-bg", "Select a valid blood group");
      ok = false;
    }
    if (!city || !city.trim()) {
      setE("sd-city", "City is required");
      ok = false;
    }
    if (!pass || !isValidPassword(pass)) {
      setE(
        "sd-pass",
        "Password must be at least 8 characters long and contain both letters and numbers",
      );
      ok = false;
    }
    if (!pass2) {
      setE("sd-pass2", "Confirm your password");
      ok = false;
    } else if (pass !== pass2) {
      setE("sd-pass2", "Passwords do not match");
      ok = false;
    }

    const rawLat = gv("sd-lat"),
      rawLng = gv("sd-lng");
    const dLat = rawLat !== "" ? Number(rawLat) : null;
    const dLng = rawLng !== "" ? Number(rawLng) : null;
    if (dLat !== null && (isNaN(dLat) || dLat < -90 || dLat > 90)) {
      setE("sd-lat", "Latitude must be between -90 and 90");
      ok = false;
    }
    if (dLng !== null && (isNaN(dLng) || dLng < -180 || dLng > 180)) {
      setE("sd-lng", "Longitude must be between -180 and 180");
      ok = false;
    }
    if (!ok) return;
    const payload = {
      role: "donor",
      name,
      age,
      gender,
      email,
      phone,
      bloodGroup: bg,
      city,
      state: gv("sd-state"),
      lat: dLat,
      lng: dLng,
      password: pass,
      reportData: signupRep || null,
      reportName: signupRep ? "blood_report" : null,
    };

    apiFetch(`${API_BASE}/api/auth/signup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    })
      .then(async (res) => {
        const data = await res.json();
        if (!res.ok) {
          const err = data.error || "Registration failed";
          if (err.toLowerCase().includes("email")) setE("sd-email", err);
          else toast(err, "err");
          return;
        }
        clearSignupForms();
        toast("Welcome, " + name + "! 🩸", "ok");
        login(data);
      })
      .catch(() => {
        toast(
          "Registration failed. Please check your connection and try again.",
          "err",
        );
      });
  } else if (role === "hospital") {
    const ids = [
      "sh-name",
      "sh-reg",
      "sh-email",
      "sh-phone",
      "sh-city",
      "sh-pass",
      "sh-pass2",
      "sh-lat",
      "sh-lng",
    ];
    ids.forEach(clrE);
    const name = gv("sh-name"),
      reg = gv("sh-reg"),
      email = gv("sh-email"),
      phone = gv("sh-phone"),
      city = gv("sh-city"),
      pass = gv("sh-pass"),
      pass2 = gv("sh-pass2");
    let ok = true;
    if (!name || !isValidName(name)) {
      setE("sh-name", "Valid hospital name required (cannot be numbers only)");
      ok = false;
    }
    if (!reg || !reg.trim()) {
      setE("sh-reg", "Registration / License number required");
      ok = false;
    }
    if (!email || !isValidEmail(email)) {
      setE("sh-email", "Valid email address required");
      ok = false;
    }
    if (!phone || !isValidPhone(phone)) {
      setE(
        "sh-phone",
        "Must be a valid 10-digit Indian mobile number (starting with 6-9)",
      );
      ok = false;
    }
    if (!city || !city.trim()) {
      setE("sh-city", "City is required");
      ok = false;
    }
    if (!pass || !isValidPassword(pass)) {
      setE(
        "sh-pass",
        "Password must be at least 8 characters long and contain both letters and numbers",
      );
      ok = false;
    }
    if (!pass2) {
      setE("sh-pass2", "Confirm your password");
      ok = false;
    } else if (pass !== pass2) {
      setE("sh-pass2", "Passwords do not match");
      ok = false;
    }

    const rawLat = gv("sh-lat"),
      rawLng = gv("sh-lng");
    const hLat = rawLat !== "" ? Number(rawLat) : null;
    const hLng = rawLng !== "" ? Number(rawLng) : null;
    if (hLat !== null && (isNaN(hLat) || hLat < -90 || hLat > 90)) {
      setE("sh-lat", "Latitude must be between -90 and 90");
      ok = false;
    }
    if (hLng !== null && (isNaN(hLng) || hLng < -180 || hLng > 180)) {
      setE("sh-lng", "Longitude must be between -180 and 180");
      ok = false;
    }
    if (!ok) return;
    const payload = {
      role: "hospital",
      name,
      registrationNumber: reg,
      email,
      phone,
      city,
      state: gv("sh-state"),
      lat: hLat,
      lng: hLng,
      password: pass,
    };

    apiFetch(`${API_BASE}/api/auth/signup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    })
      .then(async (res) => {
        const data = await res.json();
        if (!res.ok) {
          const err = data.error || "Registration failed";
          if (err.toLowerCase().includes("email")) setE("sh-email", err);
          else if (
            err.toLowerCase().includes("license") ||
            err.toLowerCase().includes("registration")
          )
            setE("sh-reg", err);
          else toast(err, "err");
          return;
        }
        clearSignupForms();
        toast("Hospital registered! Welcome 🏥", "ok");
        login(data);
      })
      .catch(() => {
        toast(
          "Registration failed. Please check your connection and try again.",
          "err",
        );
      });
  } else if (role === "bloodbank") {
    doSignupBloodBank();
  }
}

function doSignupBloodBank() {
  const ids = [
    "bb-name",
    "bb-reg",
    "bb-phone",
    "bb-email",
    "bb-city",
    "bb-hours",
    "bb-pass",
    "bb-pass2",
    "bb-lat",
    "bb-lng",
  ];
  ids.forEach(clrE);
  const name = gv("bb-name"),
    license = gv("bb-reg"),
    phone = gv("bb-phone"),
    email = gv("bb-email"),
    city = gv("bb-city"),
    state = gv("bb-state"),
    hours = gv("bb-hours"),
    pass = gv("bb-pass"),
    pass2 = gv("bb-pass2");
  let ok = true;
  if (!name || !isValidName(name)) {
    setE("bb-name", "Valid blood bank name required (cannot be numbers only)");
    ok = false;
  }
  if (!license || !license.trim()) {
    setE("bb-reg", "Registration / License ID required");
    ok = false;
  }
  if (!phone || !isValidPhone(phone)) {
    setE(
      "bb-phone",
      "Must be a valid 10-digit Indian mobile number (starting with 6-9)",
    );
    ok = false;
  }
  if (!email || !isValidEmail(email)) {
    setE("bb-email", "Valid email address required");
    ok = false;
  }
  if (!city || !city.trim()) {
    setE("bb-city", "City is required");
    ok = false;
  }
  if (!hours || !hours.trim()) {
    setE("bb-hours", "Operating hours required");
    ok = false;
  }
  if (!pass || !isValidPassword(pass)) {
    setE(
      "bb-pass",
      "Password must be at least 8 characters long and contain both letters and numbers",
    );
    ok = false;
  }
  if (!pass2) {
    setE("bb-pass2", "Confirm your password");
    ok = false;
  } else if (pass !== pass2) {
    setE("bb-pass2", "Passwords do not match");
    ok = false;
  }

  const rawLat = gv("bb-lat"),
    rawLng = gv("bb-lng");
  const bLat = rawLat !== "" ? Number(rawLat) : null;
  const bLng = rawLng !== "" ? Number(rawLng) : null;
  if (bLat !== null && (isNaN(bLat) || bLat < -90 || bLat > 90)) {
    setE("bb-lat", "Latitude must be between -90 and 90");
    ok = false;
  }
  if (bLng !== null && (isNaN(bLng) || bLng < -180 || bLng > 180)) {
    setE("bb-lng", "Longitude must be between -180 and 180");
    ok = false;
  }
  if (!ok) return;

  const payload = {
    role: "bloodbank",
    name,
    registrationNumber: license,
    phone,
    email,
    city,
    state,
    operatingHours: hours,
    lat: bLat,
    lng: bLng,
    password: pass,
  };

  apiFetch(`${API_BASE}/api/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  })
    .then(async (res) => {
      const data = await res.json();
      if (!res.ok) {
        const err = data.error || "Registration failed";
        if (err.toLowerCase().includes("email")) setE("bb-email", err);
        else if (
          err.toLowerCase().includes("license") ||
          err.toLowerCase().includes("registration")
        )
          setE("bb-reg", err);
        else toast(err, "err");
        return;
      }
      clearSignupForms();
      ensureBloodBankInventory(data.id);
      toast("Blood bank registered! Welcome 🏦", "ok");
      login(data);
    })
    .catch(() => {
      toast(
        "Registration failed. Please check your connection and try again.",
        "err",
      );
    });
}
/* ── LOCATION FRESHNESS & LOCATION UPDATE SYSTEM ── */
function getLocationFreshness(u) {
  if (!u || u.lat == null || u.lng == null || !u.locationUpdatedAt) {
    return {
      status: "very_old",
      ageHours: Infinity,
      label: "Location not updated",
      badgeHtml: '<span class="badge grey">⚠️ Location Not Set</span>',
      formattedTime: "Not updated yet",
    };
  }

  const serverD = parseServerDate(u.locationUpdatedAt);
  const updatedTime = serverD ? serverD.getTime() : NaN;
  if (isNaN(updatedTime)) {
    return {
      status: "very_old",
      ageHours: Infinity,
      label: "Location not updated",
      badgeHtml: '<span class="badge grey">⚠️ Location Not Set</span>',
      formattedTime: "Not updated yet",
    };
  }

  const diffMs = Date.now() - updatedTime;
  const ageHours = diffMs / 3600000;
  const formattedTime = ago(u.locationUpdatedAt);

  if (ageHours <= 2) {
    return {
      status: "fresh",
      ageHours: ageHours,
      label: `Updated ${formattedTime}`,
      badgeHtml: `<span class="badge green">🟢 Fresh Location (${formattedTime})</span>`,
      formattedTime,
    };
  } else if (ageHours <= 24) {
    return {
      status: "stale",
      ageHours: ageHours,
      label: `Location may be outdated (${formattedTime})`,
      badgeHtml: `<span class="badge amber">🟡 Outdated (${formattedTime})</span>`,
      formattedTime,
    };
  } else {
    return {
      status: "very_old",
      ageHours: ageHours,
      label: `Location outdated (${formattedTime})`,
      badgeHtml: `<span class="badge red">⚠️ Location Outdated (${formattedTime})</span>`,
      formattedTime,
    };
  }
}

/* Reverse geocodes lat/lng into City and State */
async function fetchReverseGeocode(lat, lng) {
  try {
    const res = await fetch(
      `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lng}&localityLanguage=en`,
      { signal: AbortSignal.timeout(5000) },
    );
    if (res.ok) {
      const data = await res.json();
      const city =
        data.city || data.locality || data.principalSubdivisionCode || "";
      const state = data.principalSubdivision || data.countryName || "";
      if (city || state) return { city, state };
    }
  } catch {}

  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lng}`,
      { signal: AbortSignal.timeout(5000) },
    );
    if (res.ok) {
      const data = await res.json();
      const addr = data.address || {};
      const city =
        addr.city ||
        addr.town ||
        addr.village ||
        addr.suburb ||
        addr.city_district ||
        addr.county ||
        "";
      const state = addr.state || "";
      if (city || state) return { city, state };
    }
  } catch {}

  return { city: "", state: "" };
}

/* Core location detection helper used by both registration & dashboard buttons */
function getCurrentLocationWithAddress() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("Geolocation is not supported by your browser."));
      return;
    }

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const lat = Number(position.coords.latitude.toFixed(6));
        const lng = Number(position.coords.longitude.toFixed(6));
        const nowIso = new Date().toISOString();

        let city = "";
        let state = "";
        try {
          const geo = await fetchReverseGeocode(lat, lng);
          city = geo.city || "";
          state = geo.state || "";
        } catch {}

        resolve({ lat, lng, city, state, locationUpdatedAt: nowIso });
      },
      (error) => {
        reject(error);
      },
      { timeout: 10000, enableHighAccuracy: true },
    );
  });
}

/* 2. DASHBOARD: "📍 Update Location" button handler */
async function updateCurrentLocationManually() {
  if (!CU) return;
  if (!navigator.geolocation) {
    toast("Geolocation is not supported by your browser.", "err");
    return;
  }

  toast("📍 Updating current GPS location & address…", "info");

  try {
    const loc = await getCurrentLocationWithAddress();

    if (CU.role === "donor") {
      const res = await apiFetch(`${API_BASE}/api/donors/${CU.id}/location`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          lat: loc.lat,
          lng: loc.lng,
          city: loc.city || null,
          state: loc.state || null,
        }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        toast(
          data.error || "Failed to update location. Please try again.",
          "err",
        );
        return;
      }

      const data = await res.json();
      CU.lat = data.lat;
      CU.lng = data.lng;
      if (data.city) CU.city = data.city;
      if (data.state) CU.state = data.state;
      if (data.locationUpdatedAt) CU.locationUpdatedAt = data.locationUpdatedAt;
    } else {
      CU.lat = loc.lat;
      CU.lng = loc.lng;
      if (loc.city) CU.city = loc.city;
      if (loc.state) CU.state = loc.state;
      CU.locationUpdatedAt = loc.locationUpdatedAt;
    }

    saveUser();
    updateSidebar();
    toast("📍 Location updated successfully!", "ok");

    const kind = roleKind();
    if (kind === "donor") {
      renderDDash();
      renderDProfile();
    } else if (kind === "hospital") {
      renderHDash();
      renderHProfile();
    } else if (kind === "bloodbank") {
      renderBDash();
    }
  } catch (err) {
    if (err && err.code === err.PERMISSION_DENIED) {
      toast(
        "Location permission is required to update your current location.",
        "err",
      );
    } else {
      toast("Unable to retrieve current location. Please try again.", "err");
    }
  }
}

function checkAutoLocationUpdateOnAppOpen() {
  if (!CU || CU.role !== "donor") return;
  const freshInfo = getLocationFreshness(CU);

  // If location is fresh (< 2 hours), avoid unnecessary GPS prompts
  if (freshInfo.status === "fresh") return;

  if (navigator.permissions && navigator.permissions.query) {
    navigator.permissions
      .query({ name: "geolocation" })
      .then((result) => {
        if (result.state === "granted") {
          navigator.geolocation.getCurrentPosition(
            async (p) => {
              const lat = Number(p.coords.latitude.toFixed(6));
              const lng = Number(p.coords.longitude.toFixed(6));
              CU.lat = lat;
              CU.lng = lng;
              CU.locationUpdatedAt = new Date().toISOString();
              try {
                const geo = await fetchReverseGeocode(lat, lng);
                if (geo.city) CU.city = geo.city;
                if (geo.state) CU.state = geo.state;
              } catch {}
              saveUser();
              updateSidebar();
              if (roleKind() === "donor") renderDDash();
            },
            () => {},
            { timeout: 8000 },
          );
        }
      })
      .catch(() => {});
  }

  checkBrowserNotificationForLocation();
}

function checkBrowserNotificationForLocation() {
  if (!CU || CU.role !== "donor") return;
  const freshInfo = getLocationFreshness(CU);
  if (freshInfo.status === "fresh") return;

  if ("Notification" in window && Notification.permission === "granted") {
    try {
      new Notification("📍 BloodLink Location Update", {
        body: "Please update your location so BloodLink can match you with nearby blood requests.",
      });
    } catch {}
  }
}

async function detectLoc(pfx) {
  if (!navigator.geolocation) {
    toast("Geolocation not supported", "err");
    return;
  }
  const btn = event?.currentTarget;
  if (btn) {
    btn.textContent = "⏳";
    btn.disabled = true;
  }
  try {
    const loc = await getCurrentLocationWithAddress();
    sv(pfx + "-lat", loc.lat);
    sv(pfx + "-lng", loc.lng);
    if (loc.city) sv(pfx + "-city", loc.city);
    if (loc.state) sv(pfx + "-state", loc.state);
    if (btn) {
      btn.textContent = "✅";
      btn.disabled = false;
    }
    const s = $(pfx + "-loc-status");
    if (s)
      s.textContent = `📍 ${loc.lat.toFixed(4)}, ${loc.lng.toFixed(4)}${loc.city ? ` (${loc.city})` : ""}`;
    toast("Location & address captured!", "ok");
  } catch {
    if (btn) {
      btn.textContent = "Retry";
      btn.disabled = false;
    }
    toast("Could not detect location", "err");
  }
}

/* 1. REGISTRATION & PROFILE FORM: "📍 Auto Detect Location" button handler */
async function getGeo(pfx) {
  if (!navigator.geolocation) {
    toast("Geolocation not supported", "err");
    return;
  }
  toast("Detecting location & address…", "info");

  try {
    const loc = await getCurrentLocationWithAddress();
    sv(pfx + "-lat", loc.lat);
    sv(pfx + "-lng", loc.lng);
    if (loc.city) sv(pfx + "-city", loc.city);
    if (loc.state) sv(pfx + "-state", loc.state);

    updateLocBadge(pfx);

    if (CU && pfx === "dp") {
      CU.lat = loc.lat;
      CU.lng = loc.lng;
      if (loc.city) CU.city = loc.city;
      if (loc.state) CU.state = loc.state;
      CU.locationUpdatedAt = loc.locationUpdatedAt;
      saveUser();
      updateSidebar();
    }

    toast("📍 Location & address detected!", "ok");
  } catch (err) {
    if (err && err.code === err.PERMISSION_DENIED) {
      toast("Location permission denied", "err");
    } else {
      toast("Could not detect location", "err");
    }
  }
}

function updateLocBadge(pfx) {
  const b = $(pfx + "-loc-badge");
  if (!b) return;
  const la = gv(pfx + "-lat"),
    lo = gv(pfx + "-lng");
  b.textContent = la && lo ? "✅ Set" : "Not set";
  b.className = "badge " + (la && lo ? "green" : "grey");
}

/* ── FILE UPLOAD ── */
function handleFile(inp, pfx) {
  const f = inp.files[0];
  if (!f) return;
  if (f.size > 5 * 1024 * 1024) {
    toast("Max 5MB", "err");
    return;
  }
  const r = new FileReader();
  r.onload = (e) => {
    signupRep = e.target.result;
    const d = $(pfx + "-drop");
    if (d) {
      d.className = "drop-zone done";
      d.innerHTML = `<div>✅</div><b>${esc(f.name)}</b><small>Report attached</small>`;
    }
    toast("Report attached!", "ok");
  };
  r.readAsDataURL(f);
}
function handleReport(inp) {
  const f = inp.files[0];
  if (!f) return;
  if (f.size > 5 * 1024 * 1024) {
    toast("Max 5MB", "err");
    return;
  }
  const r = new FileReader();
  r.onload = (e) => {
    repUrl = e.target.result;
    const d = $("dp-drop");
    if (d) {
      d.className = "drop-zone done";
      d.innerHTML = `<div>✅</div><b>${esc(f.name)}</b><small>Ready to save</small>`;
    }
    $("dp-remove-btn").style.display = "inline-flex";
    toast("Report loaded. Click Save Report.", "info");
  };
  r.readAsDataURL(f);
}
function saveReport() {
  if (repUrl) {
    CU.reportData = repUrl;
    CU.reportName = $("dp-file").files[0]?.name || "blood_report";
    saveUser();
    repUrl = null;
    toast("Report saved!", "ok");
    renderDProfile();
  } else if (CU.reportData) toast("Already saved.", "info");
  else toast("Select a file first.", "err");
}
function removeReport() {
  CU.reportData = null;
  CU.reportName = null;
  saveUser();
  repUrl = null;
  renderDProfile();
  toast("Report removed.", "info");
}
async function openReportModal(idOrData) {
  const c = $("report-content");
  if (!c) return;
  let d = idOrData;
  if (idOrData && idOrData.length < 30) {
    const u = await getUser(idOrData);
    d = u ? u.reportData : null;
  }
  if (!d) d = CU && CU.reportData;
  if (!d) {
    toast("No report available", "err");
    return;
  }
  c.innerHTML = d.startsWith("data:image")
    ? `<img src="${d}" style="width:100%;border-radius:10px"/>`
    : `<iframe src="${d}" style="width:100%;height:500px;border:none;border-radius:10px"></iframe>`;
  openModal("modal-report");
}

/* ── SESSION ── */
function login(u) {
  CU = u;
  const prevSession = DB.obj("session", {});
  const token = u.token || prevSession.token || null;
  const sessionData = { ...prevSession, id: u.id };
  if (token) {
    sessionData.token = token;
  }
  DB.set("session", sessionData);
  if (u.role === "bloodbank") {
    try {
      ensureBloodBankInventory(u.id);
    } catch {
      /* keep session; inventory can be retried on next load */
    }
  }
  if ($("l-email")) $("l-email").value = "";
  if ($("l-pass")) $("l-pass").value = "";
  ["l-email", "l-pass"].forEach(clrE);
  $("scr-auth").style.display = "none";
  $("scr-app").style.display = "block";
  initApp();
}
function doLogout() {
  CU = null;
  DB.del("session");
  $("scr-app").style.display = "none";
  $("scr-landing").style.display = "block";
  if (pollTimer) clearInterval(pollTimer);
  initLanding();
  toast("Signed out.", "info");
}
function saveUser() {
  if (!CU || !CU.id) return;
  apiFetch(`${API_BASE}/api/users/${encodeURIComponent(CU.id)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(CU),
  }).catch(() => {});
}
async function getUser(id) {
  if (!id) return {};
  try {
    const res = await apiFetch(
      `${API_BASE}/api/users/${encodeURIComponent(id)}`,
    );
    if (!res.ok) return {};
    return await res.json();
  } catch (e) {
    return {};
  }
}

/* ── APP INIT ── */
const D_NAV = [
  { id: "d-dashboard", l: "Dashboard", i: "🏠" },
  { id: "d-alerts", l: "Alerts", i: "🔔", b: true },
  { id: "d-history", l: "History", i: "📅" },
  { id: "d-tools", l: "Tools", i: "🧪" },
  { id: "d-reviews", l: "Reviews", i: "⭐" },
  { id: "d-faq", l: "FAQ", i: "❓" },
  { id: "d-profile", l: "Profile", i: "👤" },
];
const H_NAV = [
  { id: "h-dashboard", l: "Dashboard", i: "🏠" },
  { id: "h-donors", l: "Find Donors", i: "🔍" },
  { id: "h-request", l: "New Request", i: "🆘" },
  { id: "h-requests", l: "Requests", i: "📋", b: true },
  { id: "h-tools", l: "Tools", i: "🧪" },
  { id: "h-reviews", l: "Reviews", i: "⭐" },
  { id: "h-profile", l: "Profile", i: "🏥" },
];

const B_NAV = [
  { id: "b-dashboard", l: "Dashboard", i: "🏠" },
  { id: "b-alerts", l: "Blood Requests", i: "🔔", b: true },
  { id: "b-reviews", l: "Reviews", i: "⭐" },
  { id: "b-profile", l: "Profile", i: "🏦" },
];

function initApp() {
  const kind = roleKind();
  if (kind === "bloodbank") {
    try {
      ensureBloodBankInventory(CU.id);
    } catch {
      /* already persisted user; avoid duplicate inventory rows */
    }
  }
  buildNav();
  updateSidebar();
  buildRadiusSels();
  $("role-chip").textContent =
    kind === "donor"
      ? "🩸 Donor"
      : kind === "bloodbank"
        ? "🏦 Blood Bank"
        : "🏥 Hospital";
  $("role-chip").className =
    "badge " +
    (kind === "donor" ? "red" : kind === "bloodbank" ? "green" : "blue");
  $("sb-role-tag").textContent =
    kind === "donor"
      ? "Donor"
      : kind === "bloodbank"
        ? "Blood Bank"
        : "Hospital";
  $("sb-role-tag").className =
    "sb-tag " +
    (kind === "donor"
      ? "donor"
      : kind === "bloodbank"
        ? "bloodbank"
        : "hospital");
  $("notif-wrap").style.display = "block";
  showPage(
    kind === "donor"
      ? "d-dashboard"
      : kind === "bloodbank"
        ? "b-dashboard"
        : "h-dashboard",
  );
  startPoll();
  checkAutoLocationUpdateOnAppOpen();
}
function buildNav() {
  const kind = roleKind();
  const nav = kind === "donor" ? D_NAV : kind === "bloodbank" ? B_NAV : H_NAV;
  const sb = $("sb-nav"),
    bn = $("bnav-items");
  sb.innerHTML = "";
  bn.innerHTML = "";
  nav.forEach((n) => {
    const si = document.createElement("div");
    si.className = "nitem";
    si.id = "nav-" + n.id;
    si.innerHTML = `<span style="font-size:18px;width:24px;text-align:center;flex-shrink:0">${n.i}</span><span>${n.l}</span>${n.b ? `<span class="n-badge" id="nb-${n.id}" style="display:none">0</span>` : ""}`;
    si.onclick = () => {
      showPage(n.id);
      closeSidebar();
    };
    sb.appendChild(si);
    const bi = document.createElement("div");
    bi.className = "bni";
    bi.id = "bni-" + n.id;
    bi.innerHTML = `<span class="bni-icon">${n.i}</span><span>${n.l}</span>${n.b ? `<span class="bni-dot" id="bd-${n.id}"></span>` : ""}`;
    bi.onclick = () => {
      showPage(n.id);
      closeSidebar();
    };
    bn.appendChild(bi);
  });
}
function updateSidebar() {
  if (!CU) return;
  $("sb-av").textContent = (CU.name || "?")[0].toUpperCase();
  $("sb-av").className =
    "av sm " +
    (CU.role === "donor" ? "" : CU.role === "bloodbank" ? "green" : "blue");
  $("sb-name").textContent = CU.name;
  $("sb-email").textContent = CU.email;
}
function goProfile() {
  const kind = roleKind();
  showPage(
    kind === "donor"
      ? "d-profile"
      : kind === "bloodbank"
        ? "b-profile"
        : "h-profile",
  );
}
function goAlerts() {
  const kind = roleKind();
  showPage(
    kind === "donor"
      ? "d-alerts"
      : kind === "bloodbank"
        ? "b-alerts"
        : "h-requests",
  );
}
function toggleSidebar() {
  $("sidebar").classList.toggle("open");
  $("sidebar-overlay").classList.toggle("show");
}
function closeSidebar() {
  $("sidebar").classList.remove("open");
  $("sidebar-overlay").classList.remove("show");
}

/* ── ROUTING ── */
const PAGES = {
  "d-dashboard": "Dashboard",
  "d-alerts": "My Alerts",
  "d-history": "Donation History",
  "d-tools": "Tools",
  "d-reviews": "Reviews & Stories",
  "d-faq": "FAQ",
  "d-profile": "Profile",
  "h-dashboard": "Dashboard",
  "h-donors": "Find Donors",
  "h-request": "Send Request",
  "h-requests": "My Requests",
  "h-tools": "Tools",
  "h-reviews": "Reviews & Stories",
  "h-profile": "Hospital Profile",
  "b-dashboard": "Dashboard",
  "b-alerts": "Blood Requests",
  "b-reviews": "Reviews & Stories",
  "b-profile": "Blood Bank Profile",
};
const RENDERS = {
  "d-dashboard": renderDDash,
  "d-alerts": renderDAlerts,
  "d-history": renderDHistory,
  "d-tools": () => {},
  "d-reviews": renderReviewsHub,
  "d-faq": () => renderFAQ("d"),
  "d-profile": renderDProfile,
  "h-dashboard": renderHDash,
  "h-donors": renderHDonors,
  "h-request": renderHRequest,
  "h-requests": renderHRequests,
  "h-tools": () => {},
  "h-reviews": renderReviewsHub,
  "h-profile": renderHProfile,
  "b-dashboard": renderBDash,
  "b-alerts": renderBAlerts,
  "b-reviews": renderReviewsHub,
  "b-profile": renderBProfile,
};

function showPage(id) {
  const kind = roleKind();
  const prefix = kind === "donor" ? "d-" : kind === "bloodbank" ? "b-" : "h-";
  const full =
    id.startsWith("d-") || id.startsWith("h-") || id.startsWith("b-")
      ? id
      : prefix + id;
  document
    .querySelectorAll(".page")
    .forEach((p) => p.classList.remove("active"));
  $("page-" + full)?.classList.add("active");
  document
    .querySelectorAll(".nitem")
    .forEach((n) => n.classList.remove("active-d", "active-h", "active-b"));
  document
    .querySelectorAll(".bni")
    .forEach((n) => n.classList.remove("ad", "ah", "ab"));
  const nAct =
    kind === "donor"
      ? "active-d"
      : kind === "bloodbank"
        ? "active-b"
        : "active-h";
  const bAct = kind === "donor" ? "ad" : kind === "bloodbank" ? "ab" : "ah";
  $("nav-" + full)?.classList.add(nAct);
  $("bni-" + full)?.classList.add(bAct);
  $("topbar-title").textContent = PAGES[full] || "BloodLink";
  document.title = "BloodLink — " + (PAGES[full] || "");
  if (RENDERS[full]) RENDERS[full]();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function switchTab(page, tab, btn, role) {
  document
    .querySelectorAll(`#page-${page} .tc`)
    .forEach((t) => t.classList.remove("active"));
  $(`${page}-${tab}`)?.classList.add("active");
  btn
    .closest(".tabs")
    ?.querySelectorAll(".tbtn")
    .forEach((b) => {
      b.classList.remove("active");
    });
  btn.classList.add("active");
}

/* ── POLLING ── */
let pollTimer = null;
function startPoll() {
  if (pollTimer) clearInterval(pollTimer);
  updateBadge();
  pollTimer = setInterval(updateBadge, 5000);
}
function updateBadge() {
  if (!CU) return;
  let cnt = 0;
  if (CU.role === "donor") {
    cnt = myAlerts().filter((a) => a.dStatus === "pending").length;
    const b = $("nb-d-alerts");
    if (b) {
      b.textContent = cnt;
      b.style.display = cnt > 0 ? "" : "none";
    }
    const bd = $("bd-d-alerts");
    if (bd) bd.classList.toggle("show", cnt > 0);
  } else if (CU.role === "hospital") {
    cnt = hospitalRequestsCache.filter(
      (r) => r.hospitalId === CU.id && r.status === "open",
    ).length;
    const b = $("nb-h-requests");
    if (b) {
      b.textContent = cnt;
      b.style.display = cnt > 0 ? "" : "none";
    }
    const bd = $("bd-h-requests");
    if (bd) bd.classList.toggle("show", cnt > 0);
  } else if (CU.role === "bloodbank") {
    cnt = myBloodBankAlerts().filter((a) => a.bankStatus === "pending").length;
    const b = $("nb-b-alerts");
    if (b) {
      b.textContent = cnt;
      b.style.display = cnt > 0 ? "" : "none";
    }
    const bd = $("bd-b-alerts");
    if (bd) bd.classList.toggle("show", cnt > 0);
  }
  const nd = $("notif-dot");
  if (nd) nd.style.display = cnt > 0 ? "block" : "none";
}

/* ═════════════════════════════════════════════
   DONOR PAGES
═════════════════════════════════════════════ */
function renderDDash() {
  if (!CU) return;
  const freshInfo = getLocationFreshness(CU);
  $("d-wname").textContent = "Hello, " + CU.name.split(" ")[0] + "! 👋";

  const locText =
    CU.lat != null && CU.lng != null
      ? `📍 ${CU.city || "Location set"} (${CU.lat.toFixed(4)}, ${CU.lng.toFixed(4)}) · Updated ${freshInfo.formattedTime}`
      : `📍 ${CU.city || "Location not set"}`;
  $("d-wloc").textContent = locText;

  const wb = $("d-wbg");
  if (wb) {
    wb.textContent = CU.bloodGroup || "?";
    wb.className = "bgb md " + bgClass(CU.bloodGroup);
  }
  updateAvailBtn();

  const bannerEl = $("d-loc-banner");
  if (bannerEl) {
    if (freshInfo.status !== "fresh") {
      bannerEl.style.display = "block";
      bannerEl.innerHTML = `
        <div class="card" style="background:var(--am-pale);border:1.5px solid var(--am-mid);margin-bottom:16px">
          <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
            <div style="font-size:28px">📍</div>
            <div style="flex:1;min-width:200px">
              <div style="font-weight:800;font-size:15px;color:var(--text)">📍 Please update your location</div>
              <div style="font-size:13px;color:var(--text-m);margin-top:2px">
                Your current location helps BloodLink find nearby blood requests and send you relevant alerts.
                ${freshInfo.formattedTime !== "Not updated yet" ? `Last updated: ${freshInfo.formattedTime}` : "Location not set yet"}
              </div>
            </div>
            <button class="btn green sm" onclick="updateCurrentLocationManually()">📍 Update Location</button>
          </div>
        </div>`;
    } else {
      bannerEl.style.display = "none";
    }
  }

  Promise.all([fetchDonationHistory(CU.id), fetchDonorAlerts()]).then(
    ([hist, alerts]) => {
      const pend = alerts.filter((a) => a.dStatus === "pending").length;
      const lastD = CU.lastDonation || getLastHist();
      const r = elig(lastD, CU.nextEligibleDate);

      $("d-stats").innerHTML = `
      <div class="sc"><div class="sc-n r">${hist.length}</div><div class="sc-l">Donations</div></div>
      <div class="sc"><div class="sc-n a">${pend}</div><div class="sc-l">Pending Alerts</div></div>
      <div class="sc"><div class="sc-n g">${alerts.filter((a) => a.dStatus === "accepted").length}</div><div class="sc-l">Accepted</div></div>
      <div class="sc"><div class="sc-n ${!r.ok ? "a" : CU.available ? "g" : "r"}">${!r.ok ? "Resting" : CU.available ? "Active" : "Inactive"}</div><div class="sc-l">My Status</div></div>`;
      $("d-qa").innerHTML = `
      <div class="qa-btn qg" onclick="updateCurrentLocationManually()"><div class="qa-icon">📍</div><div class="qa-label">Update Location</div><div class="qa-sub">${freshInfo.formattedTime}</div></div>
      <div class="qa-btn qr" onclick="showPage('d-alerts')"><div class="qa-icon">🔔</div><div class="qa-label">Alerts</div><div class="qa-sub">${pend} pending</div></div>
      <div class="qa-btn" onclick="openModal('modal-log')"><div class="qa-icon">📅</div><div class="qa-label">Log Donation</div><div class="qa-sub">Record it</div></div>
      <div class="qa-btn" onclick="showPage('d-profile')"><div class="qa-icon">👤</div><div class="qa-label">Profile</div><div class="qa-sub">Update details</div></div>`;

      const ee = $("d-elig");
      if (ee) {
        if (!r.ok) {
          ee.innerHTML = `
          <div class="card" style="background:var(--am-pale);border:1.5px solid var(--am-mid);padding:16px;border-radius:12px;margin-bottom:0">
            <div style="display:flex;align-items:flex-start;gap:14px;flex-wrap:wrap">
              <div style="font-size:32px;line-height:1">⏳</div>
              <div style="flex:1;min-width:240px">
                <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap">
                  <div style="font-weight:800;font-size:16px;color:var(--text)">⏳ Donation Rest Period</div>
                  <span class="badge sm amber" style="font-weight:700">${r.left} day${r.left === 1 ? "" : "s"} remaining</span>
                </div>
                <div style="font-size:13px;color:var(--text-m);margin-top:4px">
                  You recently donated blood. You cannot receive donation requests during this rest period.
                </div>
                
                <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin:12px 0;padding:12px;background:var(--card);border-radius:8px;border:1px solid var(--border)">
                  <div>
                    <div style="font-size:11px;font-weight:700;color:var(--text-m);text-transform:uppercase;letter-spacing:0.5px">Last Donation</div>
                    <div style="font-weight:700;font-size:14px;color:var(--text);margin-top:2px">📅 ${fmt(r.lastDonation)}</div>
                  </div>
                  <div>
                    <div style="font-size:11px;font-weight:700;color:var(--text-m);text-transform:uppercase;letter-spacing:0.5px">Next Eligible Date</div>
                    <div style="font-weight:700;font-size:14px;color:var(--gr-dark);margin-top:2px">✨ ${fmt(r.nextEligible)}</div>
                  </div>
                </div>

                <div style="font-size:12px;color:var(--text-m);padding:8px 10px;background:rgba(0,0,0,0.03);border-radius:6px;border-left:3px solid var(--am-dark)">
                  <strong>Availability Preference:</strong> ${CU.available ? "● ON" : "○ OFF"} · <span style="font-size:11.5px">Your preference will apply automatically once your eligibility is restored on ${fmt(r.nextEligible)}.</span>
                </div>
              </div>
            </div>
          </div>`;
        } else if (CU.available) {
          ee.innerHTML = `<div class="elig-res elig-ok" style="display:block"><strong>🟢 Available for Donation</strong> — You are eligible and willing to donate.${r.days > 0 ? ` (${r.days} days since last donation)` : ""}</div>`;
        } else {
          ee.innerHTML = `<div class="elig-res" style="display:block;background:var(--border-l);border:1px solid var(--border);color:var(--text)"><strong>⚪ Not Available</strong> — You are eligible to donate, but your availability preference is OFF.</div>`;
        }
      }
      const pl = $("d-pending");
      const pa = alerts.filter((a) => a.dStatus === "pending").slice(0, 3);
      pl.innerHTML = pa.length
        ? pa.map((a) => alertCard(a, true)).join("") +
          (pend > 3
            ? `<button class="btn ghost sm" style="margin-top:8px" onclick="showPage('d-alerts')">View all ${pend} →</button>`
            : ``)
        : empty(
            "🔔",
            "No Pending Alerts",
            "You'll be notified when hospitals need your blood type.",
          );
      const ac = $("d-activity");
      if (ac) {
        const items = [];
        hist.slice(0, 3).forEach((h) =>
          items.push({
            t: h.createdAt,
            i: "🩸",
            bg: "var(--cr-pale)",
            txt: `<strong>Donated</strong> at ${esc(h.location)}`,
            meta: fmt(h.date),
          }),
        );
        alerts.slice(0, 3).forEach((a) => {
          items.push({
            t: a.createdAt,
            i: "🔔",
            bg: "var(--am-pale)",
            txt: `<strong>Alert</strong> from ${esc(a.hospitalName || "Hospital")} — ${esc(a.bloodGroup)}`,
            meta: ago(a.createdAt),
          });
        });
        items.sort((a, b) => (parseServerDate(b.t)?.getTime() || 0) - (parseServerDate(a.t)?.getTime() || 0));
        ac.innerHTML =
          items
            .slice(0, 5)
            .map(
              (x) =>
                `<div class="act-item"><div class="act-icon" style="background:${x.bg}">${x.i}</div><div><div class="act-text">${x.txt}</div><div class="act-time">${x.meta}</div></div></div>`,
            )
            .join("") ||
          empty(
            "📊",
            "No Activity Yet",
            "Your donations and alerts will appear here.",
          );
      }
    },
  );
}
let donorAlertsCache = [];
let donorHistoryCache = [];

function fetchDonorAlerts() {
  if (!CU || CU.role !== "donor") return Promise.resolve([]);
  return apiFetch(`${API_BASE}/api/donors/${CU.id}/alerts`)
    .then(async (res) => {
      if (!res.ok) return donorAlertsCache;
      const data = await res.json();
      donorAlertsCache = data.map((a) => ({
        ...a,
        dStatus: a.dStatus || a.dstatus || "pending",
        dType: a.dType || a.dtype || "direct",
      }));
      return donorAlertsCache;
    })
    .catch(() => donorAlertsCache);
}

function fetchDonationHistory(donorId) {
  const dId = donorId || (CU && CU.id);
  if (!dId) return Promise.resolve([]);
  return apiFetch(`${API_BASE}/api/donors/${dId}/history`)
    .then(async (res) => {
      if (!res.ok) return donorHistoryCache;
      const data = await res.json();
      if (CU && dId === CU.id) {
        donorHistoryCache = Array.isArray(data) ? data : [];
      }
      return Array.isArray(data) ? data : [];
    })
    .catch(() => donorHistoryCache);
}

function myAlerts() {
  return donorAlertsCache || [];
}
function myHistory() {
  return donorHistoryCache || [];
}
function getLastHist() {
  const h = myHistory();
  return h.length ? h[0].date : "";
}
function updateAvailBtn() {
  const btn = $("d-avail-toggle"),
    sw = $("d-tgl-sw"),
    lb = $("d-tgl-lbl");
  if (!btn) return;
  sw.className = "tgl-sw" + (CU.available ? " on" : "");
  const lastD = CU.lastDonation || getLastHist();
  const r = elig(lastD, CU.nextEligibleDate);
  if (!r.ok) {
    lb.innerHTML = `<span style="color:var(--am-dark);font-weight:700">⏳ In Rest Period</span><div style="font-size:11px;color:var(--text-m);font-weight:500">Preference: ${CU.available ? "ON" : "OFF"}</div>`;
  } else {
    lb.textContent = CU.available
      ? "✅ Available for Donation"
      : "❌ Not Available";
  }
}
function toggleAvail() {
  if (!CU || CU.role !== "donor") return;
  const newAvail = !CU.available;

  apiFetch(`${API_BASE}/api/donors/${CU.id}/availability`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ available: newAvail }),
  })
    .then(async (res) => {
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        toast(
          data.error || "Failed to update availability. Please try again.",
          "err",
        );
        return;
      }
      const data = await res.json();
      CU.available = data.available;
      saveUser();
      updateAvailBtn();
      const lastD = CU.lastDonation || getLastHist();
      const r = elig(lastD, CU.nextEligibleDate);
      if (!r.ok) {
        toast(
          CU.available
            ? "Preference saved: ON (Will activate after rest period)."
            : "Preference saved: OFF.",
          "info",
        );
      } else {
        toast(
          CU.available
            ? "Now available for donation!"
            : "Marked as Not Available.",
          CU.available ? "ok" : "info",
        );
      }
      renderDDash();
    })
    .catch(() => {
      toast("Failed to update availability. Please try again.", "err");
    });
}

/* ALERTS */
let lastAlertGpsAttemptTime = 0;

function triggerLocationUpdateOnAlertInteraction() {
  if (!CU || CU.role !== "donor") return;
  const now = Date.now();
  // Avoid duplicate GPS requests if an update was attempted in the last 60s
  if (now - lastAlertGpsAttemptTime < 60000) return;

  // If location is already fresh (< 5 mins), do not prompt again
  if (CU.locationUpdatedAt) {
    const locD = parseServerDate(CU.locationUpdatedAt);
    const ageMs = locD ? now - locD.getTime() : Infinity;
    if (ageMs >= 0 && ageMs < 5 * 60 * 1000) {
      return;
    }
  }

  lastAlertGpsAttemptTime = now;

  if (!navigator.geolocation) return;

  navigator.geolocation.getCurrentPosition(
    async (pos) => {
      const lat = Number(pos.coords.latitude.toFixed(6));
      const lng = Number(pos.coords.longitude.toFixed(6));
      if (
        isNaN(lat) ||
        isNaN(lng) ||
        lat < -90 ||
        lat > 90 ||
        lng < -180 ||
        lng > 180
      ) {
        return;
      }

      CU.lat = lat;
      CU.lng = lng;
      CU.locationUpdatedAt = new Date().toISOString();

      try {
        const geo = await fetchReverseGeocode(lat, lng);
        if (geo.city) CU.city = geo.city;
        if (geo.state) CU.state = geo.state;
      } catch {}

      // Update backend MySQL users table (updates lat, lng, location_updated_at; registered_lat/lng untouched)
      apiFetch(`${API_BASE}/api/donors/${CU.id}/location`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          lat: lat,
          lng: lng,
          city: CU.city,
          state: CU.state,
        }),
      }).catch(() => {});

      saveUser();
      updateSidebar();
      if (roleKind() === "donor") renderDDash();
    },
    (err) => {
      // Graceful fallback: Do NOT create fake/zero coordinates or block alert workflow
    },
    { timeout: 8000, enableHighAccuracy: true, maximumAge: 60000 },
  );
}

function renderDAlerts() {
  triggerLocationUpdateOnAlertInteraction();
  fetchDonorAlerts().then(() => {
    const all = myAlerts(),
      cnt = all.filter((a) => a.dStatus === "pending").length;
    const c = $("d-alert-cnt");
    if (c) {
      c.textContent = cnt > 0 ? cnt + " pending" : "";
      c.style.display = cnt > 0 ? "" : "none";
    }
    ["pending", "accepted", "rejected", "all"].forEach((s) => {
      const items = s === "all" ? all : all.filter((a) => a.dStatus === s);
      const el = $("d-alerts-" + s);
      if (!el) return;
      el.innerHTML = items.length
        ? items
            .map((a) =>
              alertCard(
                a,
                (a.dStatus === "pending" ||
                  (s === "all" && a.dStatus === "pending")) &&
                  a.status !== "fulfilled" &&
                  a.dStatus !== "closed",
              ),
            )
            .join("")
        : empty(
            { pending: "🔔", accepted: "✅", rejected: "❌", all: "📋" }[s],
            "No " + s + " alerts",
            "",
          );
    });
    updateBadge();
  });
}
function alertCard(a, showActs) {
  const hName = a.hospitalName || "Hospital";
  const hCity = a.hospitalCity || "";
  const hPhone = a.hospitalPhone || a.contact || "N/A";
  const ui = { Normal: "🔵", Urgent: "🟠", Critical: "🔴" };
  const isSched = a.requestType === "SCHEDULED";
  const isFulfilled = a.status === "fulfilled" || a.dStatus === "closed";

  return `<div class="alert-card ${esc(a.urgency)}" onclick="triggerLocationUpdateOnAlertInteraction()">
    <div class="alert-hdr">
      <div class="bgb md ${bgClass(a.bloodGroup)}">${esc(a.bloodGroup)}</div>
      <div style="flex:1;min-width:0">
        <div style="font-weight:800;font-size:15px;font-family:var(--display)">
          ${esc(hName)}
          ${isSched ? '<span class="badge blue sm" style="margin-left:6px">📅 SCHEDULED</span>' : ""}
        </div>
        <div class="dr-meta">📍 ${esc(hCity)} · Patient: <strong>${esc(a.patientName)}</strong></div>
        ${isSched && a.reason ? `<div style="font-size:12.5px;color:var(--text-m);margin-top:2px">🎯 <strong>Cause:</strong> ${esc(a.reason)}</div>` : ""}
      </div>
      ${!isSched ? `<span class="badge urg-${esc(a.urgency)}">${ui[a.urgency] || ""} ${esc(a.urgency)}</span>` : `<span class="badge blue">📅 Scheduled</span>`}
    </div>
    <div class="alert-meta">
      <span>🩸 ${a.units} unit(s)</span><span>📞 ${esc(hPhone)}</span>
      <span>🕐 ${ago(a.createdAt)}</span>
      ${a.dType === "direct" ? '<span class="badge red">🎯 Direct</span>' : '<span class="badge amber">📋 Standby</span>'}
    </div>
    ${
      isSched
        ? `<div style="font-size:12px;color:var(--text-m);background:var(--border-l);padding:8px 10px;border-radius:6px;margin:6px 0;display:flex;gap:12px;flex-wrap:wrap">
            <span>📅 <strong>Procedure:</strong> ${fmtDateTime(a.operationTime)}</span>
            <span>⏳ <strong>Deadline:</strong> ${fmtDateTime(a.accumulationDeadline)}</span>
          </div>`
        : ""
    }
    ${a.notes ? `<div style="font-size:13px;color:var(--text-m);padding:10px;background:var(--border-l);border-radius:8px;margin-bottom:10px">💬 ${esc(a.notes)}</div>` : ""}
    ${
      showActs && !isFulfilled
        ? `<div class="alert-acts" onclick="event.stopPropagation()"><button class="btn green sm" onclick="respondAlert('${a.id}','accepted')">✅ Accept</button><button class="btn danger sm" onclick="respondAlert('${a.id}','rejected')">❌ Decline</button></div>`
        : `<div style="margin-top:8px"><span class="badge ${a.dStatus === "confirmed" ? "green" : a.dStatus === "accepted" ? "amber" : a.dStatus === "rejected" ? "grey" : isFulfilled ? "green" : "blue"}">${a.dStatus === "confirmed" ? "✅ Confirmed by Hospital" : a.dStatus === "accepted" ? "🟡 Accepted — Waiting for hospital confirmation" : a.dStatus === "rejected" ? "❌ Declined" : isFulfilled ? "✓ Request Completed" : "⏳ Pending"}</span></div>`
    }
  </div>`;
}
function respondAlert(reqId, status) {
  if (!CU || CU.role !== "donor") return;
  const normalizedStatus = status === "declined" ? "rejected" : status;

  const payload = { status: normalizedStatus };
  if (normalizedStatus === "accepted" && CU.lat != null && CU.lng != null) {
    payload.lat = CU.lat;
    payload.lng = CU.lng;
  }

  apiFetch(`${API_BASE}/api/donors/${CU.id}/alerts/${reqId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  })
    .then(async (res) => {
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        toast(data.error || "Could not update alert response.", "err");
        return;
      }
      const resData = await res.json().catch(() => ({}));
      let acceptMsg = "✅ Accepted! Hospital will contact you.";
      if (normalizedStatus === "accepted" && resData.distance != null) {
        acceptMsg = `✅ Accepted! Distance: ${resData.distance} km. Hospital will contact you.`;
      }
      toast(normalizedStatus === "accepted" ? acceptMsg : "Declined.", "ok");
      fetchDonorAlerts().then(() => {
        renderDAlerts();
        renderDDash();
      });
    })
    .catch(() => {
      toast(
        "Failed to update alert response. Please check backend connection.",
        "err",
      );
    });
}

/* HISTORY */
async function renderDHistory() {
  if (!CU) return;
  const hist = await fetchDonationHistory(CU.id);
  const last = hist.length ? hist[0].date : "";
  const e = elig(last);
  $("d-hist-stats").innerHTML = `
    <div class="sc"><div class="sc-n r">${hist.length}</div><div class="sc-l">Donations</div></div>
    <div class="sc"><div class="sc-n">${last ? fmt(last) : "—"}</div><div class="sc-l">Last Donated</div></div>
    <div class="sc"><div class="sc-n ${e.ok ? "g" : "a"}">${e.ok ? "✅ Yes" : "⏳ Wait"}</div><div class="sc-l">Eligible</div></div>
    <div class="sc"><div class="sc-n b">${hist.length ? hist.length * 450 + "ml" : "—"}</div><div class="sc-l">Total Volume</div></div>`;
  $("d-hist-table").innerHTML = hist.length
    ? `<div class="tbl-wrap"><table><thead><tr><th>Date</th><th>Location</th><th>Units</th><th>Notes</th></tr></thead><tbody>${hist.map((h) => `<tr><td>${fmt(h.date)}</td><td>${esc(h.location)}</td><td><span class="badge red">${h.units} unit(s)</span></td><td style="color:var(--text-m)">${esc(h.notes || "—")}</td></tr>`).join("")}</tbody></table></div>`
    : empty(
        "📅",
        "No Donations Yet",
        'Click "+ Log Donation" to add your first record.',
      );
}
async function logDonation() {
  ["ld-date", "ld-loc"].forEach(clrE);
  const date = gv("ld-date"),
    loc = gv("ld-loc");
  if (!date) {
    setE("ld-date", "Required");
    return;
  }
  if (!loc) {
    setE("ld-loc", "Required");
    return;
  }

  try {
    const res = await apiFetch(`${API_BASE}/api/donors/${CU.id}/history`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        date,
        location: loc,
        units: +gv("ld-units") || 1,
        notes: gv("ld-notes"),
      }),
    });

    if (res.ok) {
      if (!CU.lastDonation || date > CU.lastDonation) {
        CU.lastDonation = date;
        saveUser();
      }
      toast("Donation logged! 🩸", "ok");
      closeModal("modal-log");
      await fetchDonationHistory(CU.id);
      renderDHistory();
    } else {
      const data = await res.json().catch(() => ({}));
      toast(data.error || "Failed to log donation", "err");
    }
  } catch (err) {
    toast("Failed to log donation. Please try again.", "err");
  }
}

/* TOOLS */
function checkElig() {
  const d = gv("elig-inp"),
    r = $("elig-res");
  if (!d) {
    toast("Pick a date first", "err");
    return;
  }
  const res = elig(d);
  r.className = "elig-res " + (res.ok ? "elig-ok" : "elig-no");
  r.style.display = "block";
  r.innerHTML = `<strong>${res.ok ? "✅ Eligible!" : "⏳ Not yet."}</strong> ${res.msg}`;
}
async function renderIdCard() {
  if (!CU) return;
  const hist = await fetchDonationHistory(CU.id);
  $("id-card-preview").innerHTML = `<div class="id-card">
    <div style="font-size:10px;opacity:.6;font-weight:800;letter-spacing:2px;text-transform:uppercase;margin-bottom:16px">🩸 BloodLink · Verified Donor</div>
    <div style="font-size:52px;font-weight:900;font-family:var(--display);line-height:1;margin-bottom:10px">${esc(CU.bloodGroup || "?")}</div>
    <div style="font-size:24px;font-weight:800;font-family:var(--display);margin-bottom:20px;letter-spacing:-.3px">${esc(CU.name)}</div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;font-size:12.5px;opacity:.85">
      <div>📍 ${esc(CU.city || "—")}</div><div>📞 ${esc(CU.phone || "—")}</div>
      <div>🎂 Age ${esc(CU.age || "—")}</div><div>🆔 #${esc(CU.id.slice(-8))}</div>
      <div>🩸 ${hist.length} donation(s)</div><div>${CU.available ? "✅ Available" : "❌ Inactive"}</div>
    </div></div>`;
}
function renderDonorGuide() {
  const g = $("donor-guide");
  if (!g || g.children.length > 0) return;
  g.innerHTML = `<div class="guide-card"><div class="ctitle">🩸 How Donating Works</div>${[
    "Set status to Available on your Dashboard.",
    "Receive alerts when hospitals need your blood type.",
    "Review: hospital name, urgency, units needed.",
    "Accept or Decline in one tap.",
    "Visit hospital at agreed time. Log it in Donation History!",
  ]
    .map(
      (s, i) =>
        `<div class="g-step"><div class="g-num">${i + 1}</div><div><p>${s}</p></div></div>`,
    )
    .join("")}</div>
  <div class="guide-card"><div class="ctitle">💡 Tips</div><ul style="padding-left:20px;color:var(--text-m);font-size:13.5px;line-height:2.2">
    <li>Stay hydrated before and after donation</li><li>Eat iron-rich food beforehand</li>
    <li>Wait 90 days between whole blood donations</li><li>Keep location coordinates updated</li>
    <li>Upload blood test report — hospitals trust it</li></ul></div>`;
}

/* PROFILE */
function renderDProfile() {
  if (!CU) return;
  $("dp-av").textContent = (CU.name || "?")[0].toUpperCase();
  $("dp-name-d").textContent = CU.name;
  $("dp-email-d").textContent = CU.email;
  $("dp-bgb").textContent = CU.bloodGroup || "?";
  $("dp-bgb").className = "bgb sm " + bgClass(CU.bloodGroup);

  const freshInfo = getLocationFreshness(CU);
  const lastD = CU.lastDonation || getLastHist();
  const r = elig(lastD, CU.nextEligibleDate);
  const dpBadge = $("dp-status");
  if (dpBadge && dpBadge.parentNode) {
    const statusBadge = !r.ok
      ? `<span id="dp-status" class="badge amber">⏳ Rest Period</span>`
      : `<span id="dp-status" class="badge ${CU.available ? "green" : "red"}">${CU.available ? "✅ Available" : "❌ Not Available"}</span>`;
    dpBadge.parentNode.innerHTML = `
      <div id="dp-bgb" class="bgb sm ${bgClass(CU.bloodGroup)}">${esc(CU.bloodGroup || "?")}</div>
      ${statusBadge}
      ${freshInfo.badgeHtml}`;
  }

  sv("dp-name", CU.name);
  sv("dp-phone", CU.phone || "");
  sv("dp-bg", CU.bloodGroup || "");
  sv("dp-gender", CU.gender || "");
  sv("dp-age", CU.age || "");
  sv("dp-city", CU.city || "");
  sv("dp-state", CU.state || "");
  sv("dp-lat", CU.lat || "");
  sv("dp-lng", CU.lng || "");
  sv("dp-lastdon", CU.lastDonation || "");
  updateLocBadge("dp");

  const ri = $("dp-rep-info"),
    pb = $("dp-preview-btn"),
    rb = $("dp-remove-btn"),
    dz = $("dp-drop");
  if (CU.reportData) {
    if (ri) {
      ri.style.display = "block";
      ri.innerHTML = `<div class="rep-prev">📄 <span>${esc(CU.reportName || "blood_report")}</span> <span class="badge green">Uploaded</span></div>`;
    }
    if (pb) pb.style.display = "inline-flex";
    if (rb) rb.style.display = "inline-flex";
    if (dz) {
      dz.className = "drop-zone done";
      dz.innerHTML = `<div>✅</div><b>Report uploaded</b><small>Click to replace</small>`;
    }
  } else {
    if (ri) ri.style.display = "none";
    if (pb) pb.style.display = "none";
    if (rb) rb.style.display = "none";
    if (dz) {
      dz.className = "drop-zone";
      dz.innerHTML = `<div>📄</div><b>Click to upload blood report</b><small>PDF or Image · Max 5MB</small>`;
    }
  }
}
function saveDonorProfile() {
  CU.name = gv("dp-name");
  CU.phone = gv("dp-phone");
  CU.bloodGroup = gv("dp-bg");
  CU.gender = gv("dp-gender") || null;
  CU.age = +gv("dp-age");
  CU.city = gv("dp-city");
  CU.state = gv("dp-state");

  const newLat = +gv("dp-lat") || null;
  const newLng = +gv("dp-lng") || null;
  if (newLat !== CU.lat || newLng !== CU.lng) {
    CU.lat = newLat;
    CU.lng = newLng;
    if (newLat != null && newLng != null) {
      CU.locationUpdatedAt = new Date().toISOString();
    }
  }

  CU.lastDonation = gv("dp-lastdon");
  saveUser();
  updateSidebar();
  toast("Profile updated! ✅", "ok");
  renderDProfile();
}

/* ═════════════════════════════════════════════
   HOSPITAL PAGES
═════════════════════════════════════════════ */
let hospitalRequestsCache = [];

function fetchHospitalRequests(hospitalId) {
  if (!CU || CU.role !== "hospital") return Promise.resolve([]);
  const hId = hospitalId || CU.id;
  return apiFetch(`${API_BASE}/api/hospitals/${hId}/requests`)
    .then(async (res) => {
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        toast(data.error || "Failed to retrieve hospital requests.", "err");
        return hospitalRequestsCache;
      }
      const data = await res.json();
      hospitalRequestsCache = Array.isArray(data) ? data : [];
      return hospitalRequestsCache;
    })
    .catch(() => {
      toast("Unable to connect to backend server.", "err");
      return hospitalRequestsCache;
    });
}

function renderHDash() {
  if (!CU) return;
  $("h-wname").textContent = "Welcome, " + CU.name.split(" ")[0] + "! 🏥";
  $("h-wloc").textContent =
    "📍 " + (CU.city || "Location not set") + (CU.state ? ", " + CU.state : "");

  Promise.all([
    fetchHospitalRequests(CU.id),
    apiFetch(`${API_BASE}/api/donors/search?available=true`)
      .then((r) => r.json())
      .catch(() => []),
  ]).then(([myR, availableDonors]) => {
    const donors = Array.isArray(availableDonors) ? availableDonors : [];
    const availableDonorCount = donors.length;
    const open = myR.filter((r) => r.status === "open").length;
    const acc = myR.reduce(
      (s, r) =>
        s +
        [...(r.directDonors || []), ...(r.waitingDonors || [])].filter(
          (d) => d.status === "accepted",
        ).length,
      0,
    );
    $("h-stats").innerHTML = `
      <div class="sc"><div class="sc-n b">${myR.length}</div><div class="sc-l">Total Requests</div></div>
      <div class="sc"><div class="sc-n r">${open}</div><div class="sc-l">Open</div></div>
      <div class="sc"><div class="sc-n g">${myR.filter((r) => r.status === "fulfilled").length}</div><div class="sc-l">Fulfilled</div></div>
      <div class="sc"><div class="sc-n">${availableDonorCount}</div><div class="sc-l">Available Donors</div></div>`;
    $("h-qa").innerHTML = `
      <div class="qa-btn qb" onclick="showPage('h-request')"><div class="qa-icon">🆘</div><div class="qa-label">New Request</div><div class="qa-sub">Send emergency</div></div>
      <div class="qa-btn qb" onclick="showPage('h-donors')"><div class="qa-icon">🔍</div><div class="qa-label">Find Donors</div><div class="qa-sub">Search by group</div></div>
      <div class="qa-btn" onclick="showPage('h-requests')"><div class="qa-icon">📋</div><div class="qa-label">My Requests</div><div class="qa-sub">${open} open</div></div>
      <div class="qa-btn" onclick="showPage('h-profile')"><div class="qa-icon">🏥</div><div class="qa-label">Profile</div><div class="qa-sub">${CU.lat ? "Location set" : "Set location"}</div></div>`;
    // Blood inventory
    const grps = ["A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"];
    $("h-blood-inv").innerHTML = grps
      .map((g) => {
        const cnt = donors.filter((d) => d.bloodGroup === g).length;
        const clr =
          cnt === 0
            ? "#EF4444"
            : cnt < 3
              ? "#F97316"
              : cnt < 6
                ? "#F59E0B"
                : "var(--gr)";
        return `<div class="bi-item"><div class="bi-group">${g}</div><div class="bi-bar-wrap"><div class="bi-bar" style="width:${Math.min((cnt / 10) * 100, 100)}%;background:${clr}"></div></div><div class="bi-count">${cnt} available</div></div>`;
      })
      .join("");
    const rec = [...myR]
      .sort((a, b) => (parseServerDate(b.createdAt)?.getTime() || 0) - (parseServerDate(a.createdAt)?.getTime() || 0))
      .slice(0, 3);
    $("h-recent-req").innerHTML = rec.length
      ? rec.map((r) => reqCard(r, true)).join("")
      : empty(
          "📋",
          "No Requests Yet",
          '<a onclick="showPage(\'h-request\')" style="cursor:pointer;color:var(--bl);font-weight:700">Send your first request →</a>',
        );
    const ac = $("h-activity");
    if (ac) {
      const items = [];
      myR.slice(0, 5).forEach((r) => {
        items.push({
          t: r.createdAt,
          i: "🆘",
          bg: "var(--cr-pale)",
          txt: `<strong>Request sent</strong> — ${esc(r.bloodGroup)} for ${esc(r.patientName)}`,
          meta: ago(r.createdAt),
        });
        [...(r.directDonors || []), ...(r.waitingDonors || [])]
          .filter((d) => d.status === "accepted")
          .forEach((d) => {
            items.push({
              t: d.at || r.createdAt,
              i: "✅",
              bg: "var(--gr-pale)",
              txt: `<strong>${esc(d.name || "Donor")}</strong> accepted your request`,
              meta: ago(d.at || r.createdAt),
            });
          });
      });
      items.sort((a, b) => (parseServerDate(b.t)?.getTime() || 0) - (parseServerDate(a.t)?.getTime() || 0));
      ac.innerHTML =
        items
          .slice(0, 6)
          .map(
            (x) =>
              `<div class="act-item"><div class="act-icon" style="background:${x.bg}">${x.i}</div><div><div class="act-text">${x.txt}</div><div class="act-time">${x.meta}</div></div></div>`,
          )
          .join("") || empty("📊", "No Activity Yet", "");
    }
  });
}

/* FIND DONORS */
function renderHDonors() {
  buildBgGrid();
  buildRadiusSels();
  searchDonors();
}
function buildBgGrid() {
  const g = $("hfd-bg-grid");
  if (!g || g.children.length) return;
  const all = document.createElement("div");
  all.className = "bgf active";
  all.textContent = "All";
  all.onclick = () => {
    selBg = "";
    g.querySelectorAll(".bgf").forEach((o) => o.classList.remove("active"));
    all.classList.add("active");
    searchDonors();
  };
  g.appendChild(all);
  ["A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"].forEach((bg) => {
    const o = document.createElement("div");
    o.className = "bgf";
    o.textContent = bg;
    o.onclick = () => {
      selBg = bg;
      g.querySelectorAll(".bgf").forEach((x) => x.classList.remove("active"));
      o.classList.add("active");
      searchDonors();
    };
    g.appendChild(o);
  });
}
async function searchDonors() {
  const params = new URLSearchParams();
  if (selBg) params.append("bloodGroup", selBg);
  if (gv("hfd-avail") === "1") params.append("available", "true");
  const city = gv("hfd-city");
  if (city && city.trim()) params.append("city", city.trim());
  if (CU && CU.lat != null && CU.lng != null) {
    params.append("lat", CU.lat);
    params.append("lng", CU.lng);
    if (selR) params.append("radius", selR);
  }

  try {
    const res = await apiFetch(
      `${API_BASE}/api/donors/search?${params.toString()}`,
    );
    const list = res.ok ? await res.json() : [];
    const dc = $("donor-count");
    if (dc)
      dc.innerHTML = list.length
        ? `Found <strong>${list.length}</strong> donor(s)`
        : "";
    $("hfd-results").innerHTML = list.length
      ? list.map(donorRow).join("")
      : empty(
          "🔍",
          "No Donors Found",
          "Try increasing the radius or changing blood group filter.",
        );
  } catch (err) {
    const dc = $("donor-count");
    if (dc) dc.innerHTML = "";
    $("hfd-results").innerHTML = empty(
      "🔍",
      "No Donors Found",
      "Try increasing the radius or changing blood group filter.",
    );
  }
}
function clearSearch() {
  selBg = "";
  selR = 5;
  $("hfd-bg-grid")
    ?.querySelectorAll(".bgf")
    .forEach((o, i) => o.classList.toggle("active", i === 0));
  $("hfd-radius")
    ?.querySelectorAll(".rs")
    .forEach((o, i) => o.classList.toggle("active", i === 1));
  sv("hfd-avail", "");
  sv("hfd-city", "");
  searchDonors();
}
function donorRow(d) {
  const e = elig(d.lastDonation);
  const fresh = getLocationFreshness(d);
  const ds =
    d._d != null && d._d < 900
      ? `📍 ${d._d.toFixed(1)}km away`
      : `📍 ${esc(d.city || "Unknown")}`;
  return `<div class="donor-row" onclick="showDonorDetail('${d.id}')">
    <div class="bgb md ${bgClass(d.bloodGroup)}">${esc(d.bloodGroup || "?")}</div>
    <div class="dr-info">
      <div class="dr-name">${esc(d.name)}</div>
      <div class="dr-meta">${ds}${d.city ? ` · ${esc(d.city)}${d.state ? ", " + esc(d.state) : ""}` : ""} · Age ${d.age || "N/A"}</div>
      <div class="dr-meta" style="margin-top:4px">
        ${d.available ? '<span class="badge green">✅ Available</span>' : '<span class="badge red">❌ Unavailable</span>'}
        ${e.ok ? '<span class="badge green">✅ Eligible</span>' : '<span class="badge amber">⏳ Waiting</span>'}
        ${fresh.badgeHtml}
        ${d.reportData ? '<span class="badge blue">📄 Report</span>' : ""}
      </div>
    </div>
    ${d._d != null && d._d < 900 ? `<div style="text-align:right;font-size:12px;color:var(--bl);font-weight:700"><strong style="font-size:16px">${d._d.toFixed(1)}</strong><br>km</div>` : ""}
  </div>`;
}
async function showDonorDetail(id) {
  const d = await getUser(id);
  if (!d || !d.id) return;
  const e = elig(d.lastDonation),
    ds = dist(CU.lat, CU.lng, d.lat, d.lng);
  const fresh = getLocationFreshness(d);
  $("donor-detail").innerHTML = `
    <div style="display:flex;align-items:center;gap:16px;margin-bottom:20px">
      <div class="bgb xl ${bgClass(d.bloodGroup)}">${esc(d.bloodGroup)}</div>
      <div><div style="font-family:var(--display);font-size:22px;font-weight:800">${esc(d.name)}</div>
      <div style="color:var(--text-m);margin-top:4px">📍 ${esc(d.city || "—")}${d.state ? ", " + esc(d.state) : ""}</div>
      ${ds != null ? `<div style="font-size:13px;color:var(--bl);margin-top:3px;font-weight:700">📍 ${ds.toFixed(1)}km from your hospital</div>` : ""}</div>
    </div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:14px">
      <div style="background:var(--border-l);border-radius:10px;padding:12px"><div style="font-size:11px;color:var(--text-m);font-weight:700;text-transform:uppercase;letter-spacing:.4px">Phone</div><div style="font-weight:700;margin-top:4px">${esc(d.phone || "—")}</div></div>
      <div style="background:var(--border-l);border-radius:10px;padding:12px"><div style="font-size:11px;color:var(--text-m);font-weight:700;text-transform:uppercase;letter-spacing:.4px">Age</div><div style="font-weight:700;margin-top:4px">${d.age || "—"}</div></div>
      <div style="background:var(--border-l);border-radius:10px;padding:12px"><div style="font-size:11px;color:var(--text-m);font-weight:700;text-transform:uppercase;letter-spacing:.4px">Status</div><div style="margin-top:4px">${d.available ? '<span class="badge green">✅ Available</span>' : '<span class="badge red">❌ Unavailable</span>'}</div></div>
      <div style="background:var(--border-l);border-radius:10px;padding:12px"><div style="font-size:11px;color:var(--text-m);font-weight:700;text-transform:uppercase;letter-spacing:.4px">Eligibility</div><div style="margin-top:4px">${e.ok ? '<span class="badge green">✅ Eligible</span>' : '<span class="badge amber">⏳ Waiting</span>'}</div></div>
    </div>
    <div style="background:var(--border-l);border-radius:10px;padding:12px;margin-bottom:14px">
      <div style="font-size:11px;color:var(--text-m);font-weight:700;text-transform:uppercase;letter-spacing:.4px;margin-bottom:4px">Location Freshness</div>
      <div style="display:flex;align-items:center;gap:8px">${fresh.badgeHtml} <span style="font-size:12.5px;color:var(--text-m)">Last updated: ${fresh.formattedTime}</span></div>
    </div>
    <div style="background:var(--border-l);border-radius:10px;padding:12px;margin-bottom:12px">
    </div>
    ${d.reportData ? `<button class="btn blue sm" style="margin-bottom:12px" onclick="previewReport('${id}')">👁 View Blood Report</button>` : ""}
    <a href="tel:${esc(d.phone)}" class="btn green sm">📞 Call Donor</a>`;
  openModal("modal-donor");
}

/* SEND REQUEST */
function buildRadiusSels() {
  buildRS("hfd-radius", selR, "", (r) => {
    selR = r;
  });
  buildRS("hr-radius", selHR, "b", (r) => {
    selHR = r;
    const h = $("hr-radius-hint");
    if (h) h.textContent = `Will search donors within ${r}km`;
  });
}
function buildRS(cid, def, cls, cb) {
  const c = $(cid);
  if (!c || c.children.length) return;
  [2, 5, 10, 15, 25, 50].forEach((r) => {
    const o = document.createElement("div");
    o.className = "rs" + (r === def ? " active" + (cls ? " " + cls : "") : "");
    o.textContent = r < 50 ? r + "km" : "Any";
    o.onclick = () => {
      c.querySelectorAll(".rs").forEach((x) => (x.className = "rs"));
      o.className = "rs active" + (cls ? " " + cls : "");
      cb(r);
    };
    c.appendChild(o);
  });
}

function setReqType(type) {
  const norm = type === "SCHEDULED" ? "SCHEDULED" : "EMERGENCY";
  sv("hr-type", norm);

  const btnEmerg = $("btn-type-emergency");
  const btnSched = $("btn-type-scheduled");
  const schedFields = $("hr-scheduled-fields");
  const urgWrap = $("hr-urgency-wrap");
  const sendBtn = $("send-req-btn");

  if (btnEmerg && btnSched) {
    if (norm === "SCHEDULED") {
      btnSched.className = "btn blue";
      btnEmerg.className = "btn ghost";
      if (schedFields) schedFields.style.display = "block";
      if (urgWrap) urgWrap.style.display = "none";
      if (sendBtn)
        sendBtn.textContent = "📅 Create Scheduled Request & Match All";
    } else {
      btnEmerg.className = "btn blue";
      btnSched.className = "btn ghost";
      if (schedFields) schedFields.style.display = "none";
      if (urgWrap) urgWrap.style.display = "block";
      if (sendBtn) sendBtn.textContent = "🆘 Send Emergency Request";
    }
  }
}

async function matchDonorsForBloodRequest(requestId) {
  const res = await apiFetch(
    `${API_BASE}/api/requests/${encodeURIComponent(requestId)}/match-donors`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
    },
  );
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || "Failed to match donors from server");
  }
  return await res.json();
}

async function matchBloodBanksForBloodRequest(requestId) {
  const res = await apiFetch(
    `${API_BASE}/api/requests/${encodeURIComponent(requestId)}/match-bloodbanks`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
    },
  );
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || "Failed to match blood banks from server");
  }
  return await res.json();
}

function renderHRequest() {
  if (!CU) return;
  if ($("hr-contact")) {
    sv("hr-contact", CU.phone || "");
  }
}

function sendRequest() {
  [
    "hr-patient",
    "hr-bg",
    "hr-units",
    "hr-urgency",
    "hr-reason",
    "hr-op-time",
    "hr-deadline",
    "hr-contact",
  ].forEach(clrE);

  const reqType = gv("hr-type") || "EMERGENCY",
    pt = gv("hr-patient"),
    bg = gv("hr-bg"),
    rawUnits = gv("hr-units"),
    units = Number(rawUnits),
    urg = gv("hr-urgency") || "Normal",
    reason = gv("hr-reason"),
    opTime = gv("hr-op-time"),
    deadline = gv("hr-deadline"),
    ct = CU && CU.phone ? CU.phone.trim() : gv("hr-contact"),
    notes = gv("hr-notes");

  let ok = true;
  if (!pt || !isValidName(pt)) {
    setE("hr-patient", "Valid patient name required (cannot be numbers only)");
    ok = false;
  }
  if (!bg || !BB_GROUPS.includes(bg)) {
    setE("hr-bg", "Valid blood group required");
    ok = false;
  }
  if (!rawUnits || isNaN(units) || !Number.isInteger(units) || units < 1) {
    setE("hr-units", "Units required must be a whole number greater than 0");
    ok = false;
  }

  if (reqType === "SCHEDULED") {
    if (!reason || !reason.trim()) {
      setE(
        "hr-reason",
        "Reason / clinical cause is required for scheduled requests",
      );
      ok = false;
    }
    if (!opTime || !opTime.trim()) {
      setE("hr-op-time", "Operation / requirement date & time is required");
      ok = false;
    }
    if (!deadline || !deadline.trim()) {
      setE("hr-deadline", "Accumulation deadline is required");
      ok = false;
    } else {
      const dDate = new Date(deadline);
      const oDate = new Date(opTime);
      const now = new Date();
      if (dDate <= now) {
        setE("hr-deadline", "Accumulation deadline must be in the future");
        ok = false;
      } else if (opTime && dDate >= oDate) {
        setE(
          "hr-deadline",
          "Accumulation deadline must be strictly before operation time",
        );
        ok = false;
      }
    }
  } else {
    if (!urg || !urg.trim()) {
      setE("hr-urgency", "Urgency level is required");
      ok = false;
    }
  }

  if (!ct || !isValidPhone(ct)) {
    setE(
      "hr-contact",
      "Must be a valid 10-digit Indian mobile number (starting with 6-9)",
    );
    ok = false;
  }
  if (!selHR || isNaN(selHR) || selHR <= 0 || selHR > 100) {
    toast("Search radius must be between 1km and 100km", "err");
    ok = false;
  }
  if (!ok) return;

  const btn = $("send-req-btn");
  btn.disabled = true;
  btn.textContent =
    reqType === "SCHEDULED"
      ? "🔍 Creating scheduled request & matching…"
      : "🔍 Creating request & matching blood banks…";

  const payload = {
    hospitalId: CU.id,
    patientName: pt,
    bloodGroup: bg,
    units: units,
    urgency: reqType === "SCHEDULED" ? "Normal" : urg,
    contact: ct,
    notes: notes || null,
    radius: selHR,
    requestType: reqType,
    reason: reqType === "SCHEDULED" ? reason.trim() : null,
    operationTime:
      reqType === "SCHEDULED" && opTime
        ? new Date(opTime).toISOString().slice(0, 19)
        : null,
    accumulationDeadline:
      reqType === "SCHEDULED" && deadline
        ? new Date(deadline).toISOString().slice(0, 19)
        : null,
  };

  apiFetch(`${API_BASE}/api/requests`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  })
    .then(async (res) => {
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        btn.disabled = false;
        btn.textContent =
          reqType === "SCHEDULED"
            ? "📅 Create Scheduled Request & Match All"
            : "🆘 Send Emergency Request";
        toast(data.error || "Failed to create blood request.", "err");
        return;
      }
      const data = await res.json();

      // Match blood banks from backend MySQL
      let structuredBanks = [];
      try {
        const bbMatchRes = await matchBloodBanksForBloodRequest(data.id);
        structuredBanks = bbMatchRes.bloodBanks || [];
      } catch (err) {
        console.error("Failed to match blood banks from server:", err);
      }

      // Persist matched blood banks to MySQL request_blood_banks
      if (structuredBanks && structuredBanks.length > 0) {
        apiFetch(
          `${API_BASE}/api/requests/${encodeURIComponent(data.id)}/bloodbanks`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              bloodBanks: structuredBanks.map((sb) => ({
                bloodBankId: sb.id,
                distance: sb.distance,
                availableUnitsAtMatch: sb.availableUnitsAtMatch,
                status: sb.status,
                responseStatus: sb.responseStatus,
              })),
            }),
          },
        ).catch(() => {});
      }

      let directDonors = [];
      let waitingDonors = [];
      let donorAlertsSent = false;

      // For SCHEDULED requests: Automatically match and alert Donors immediately!
      if (reqType === "SCHEDULED") {
        try {
          const donorMatches = await matchDonorsForBloodRequest(data.id);
          directDonors = donorMatches.direct || [];
          waitingDonors = donorMatches.waiting || [];

          if (directDonors.length > 0 || waitingDonors.length > 0) {
            const donorPayloadItems = [
              ...directDonors.map((d) => ({
                donorId: d.id,
                alertType: "direct",
              })),
              ...waitingDonors.map((d) => ({
                donorId: d.id,
                alertType: "waiting",
              })),
            ];

            await apiFetch(
              `${API_BASE}/api/requests/${encodeURIComponent(data.id)}/alerts`,
              {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  donors: donorPayloadItems,
                  remainingUnitsAtDonorAlert: units,
                }),
              },
            );
            donorAlertsSent = true;
          }
        } catch (err) {
          console.error(
            "Failed to match/alert donors for scheduled request:",
            err,
          );
        }
      }

      const req = {
        ...data,
        securedUnits: data.securedUnits || 0,
        donorAlertsSent: donorAlertsSent,
        directDonors: directDonors,
        waitingDonors: waitingDonors,
        bloodBanks: structuredBanks,
      };

      hospitalRequestsCache.unshift(req);

      btn.disabled = false;
      btn.textContent =
        reqType === "SCHEDULED"
          ? "📅 Create Scheduled Request & Match All"
          : "🆘 Send Emergency Request";
      toast(
        reqType === "SCHEDULED"
          ? "Scheduled blood request created! Blood Banks & Donors alerted 📅"
          : "Blood request created successfully! 🩸",
        "ok",
      );
      showMatchResult(req, structuredBanks);

      [
        "hr-patient",
        "hr-bg",
        "hr-units",
        "hr-urgency",
        "hr-reason",
        "hr-op-time",
        "hr-deadline",
        "hr-notes",
      ].forEach((id) => sv(id, ""));
      sv("hr-contact", CU && CU.phone ? CU.phone : "");
    })
    .catch(() => {
      btn.disabled = false;
      btn.textContent =
        reqType === "SCHEDULED"
          ? "📅 Create Scheduled Request & Match All"
          : "🆘 Send Emergency Request";
      toast(
        "Failed to create blood request. Please check backend connection.",
        "err",
      );
    });
}

async function alertDonorsForRequest(reqId) {
  const req = hospitalRequestsCache.find((r) => r.id === reqId);
  if (!req) return;

  const secured = req.securedUnits || 0;
  const remaining = Math.max(0, req.units - secured);
  if (secured >= req.units || remaining === 0) {
    toast("Request is already fully secured!", "info");
    return;
  }

  try {
    const { direct, waiting } = await matchDonorsForBloodRequest(reqId);

    const donorPayloadItems = [
      ...(direct || []).map((d) => ({ donorId: d.id, alertType: "direct" })),
      ...(waiting || []).map((d) => ({ donorId: d.id, alertType: "waiting" })),
    ];

    const res = await apiFetch(
      `${API_BASE}/api/requests/${encodeURIComponent(reqId)}/alerts`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          donors: donorPayloadItems,
          remainingUnitsAtDonorAlert: remaining,
        }),
      },
    );

    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      toast(data.error || "Failed to send donor alerts.", "err");
      return;
    }

    const createdAlerts = await res.json().catch(() => []);
    const alertCount = Array.isArray(createdAlerts) ? createdAlerts.length : 0;

    req.donorAlertsSent = true;
    req.remainingUnitsAtDonorAlert = remaining;

    if (alertCount > 0) {
      toast(
        `🩸 Alerted ${alertCount} donor(s) for remaining ${remaining} unit(s)!`,
        "ok",
      );
    } else {
      toast("No new eligible donors to alert within current radius.", "info");
    }

    if ($("scr-app") && $("scr-app").style.display !== "none") {
      fetchHospitalRequests().then(() => {
        renderHRequests();
        renderHDash();
      });
    }
  } catch (err) {
    toast(err.message || "Failed to match and alert donors.", "err");
  }
}

async function increaseRequestRadius(reqId) {
  if (!CU || CU.role !== "hospital") return;

  const btn = event?.currentTarget;
  if (btn) {
    btn.disabled = true;
    btn.textContent = "📍 Increasing…";
  }

  try {
    const res = await apiFetch(
      `${API_BASE}/api/requests/${encodeURIComponent(reqId)}/increase-radius`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
      },
    );

    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      toast(data.error || "Failed to increase search radius.", "err");
      if (btn) {
        btn.disabled = false;
        btn.textContent = "📍 Increase Radius";
      }
      return;
    }

    const data = await res.json();
    const fresh = data.request;
    const newR = data.newRadius;
    const newBb = data.newBloodBanksCount || 0;
    const newD = data.newDonorsCount || 0;

    const idx = hospitalRequestsCache.findIndex((x) => x.id === reqId);
    if (idx >= 0 && fresh) {
      hospitalRequestsCache[idx] = fresh;
    }

    toast(
      `📍 Search radius increased to ${newR}km! (${newBb} new blood bank(s), ${newD} new potential donor(s))`,
      "ok",
    );

    fetchHospitalRequests().then(() => {
      renderHRequests();
      renderHDash();
      showReqDetail(reqId);
    });
  } catch (err) {
    toast("Failed to increase radius. Please check backend connection.", "err");
    if (btn) {
      btn.disabled = false;
      btn.textContent = "📍 Increase Radius";
    }
  }
}

function renderMatchedBankCard(mb, reqId, isFulfilled, isExpired) {
  const bb = mb;
  const isSufficient = mb.status === "sufficient";
  const badgeClass = isSufficient ? "green" : "amber";
  const badgeLabel = isSufficient
    ? "🟢 Sufficient Stock"
    : `🟡 ${mb.availableUnitsAtMatch}/${mb.availableUnitsAtMatch} Partial Stock`;
  const locStr = bb.city
    ? bb.state
      ? `${esc(bb.city)}, ${esc(bb.state)}`
      : esc(bb.city)
    : "";
  const resp = mb.responseStatus || "pending";
  const isAccepted = resp === "accepted";
  const isConfirmed = resp === "confirmed";
  const isDeclined = resp === "rejected" || resp === "declined";
  const respBadge = isConfirmed
    ? `<span class="badge green">✅ Confirmed (${mb.unitsSecured || mb.availableUnitsAtMatch} units)</span>`
    : isAccepted
      ? `<span class="badge amber">🟡 Accepted (${mb.reservedUnits !== undefined ? mb.reservedUnits : mb.unitsSecured || mb.availableUnitsAtMatch} units reserved)</span>`
      : isDeclined
        ? `<span class="badge grey">❌ Rejected</span>`
        : `<span class="badge blue">⏳ Pending Response</span>`;

  const actionBtns =
    isAccepted && reqId && !isFulfilled && !isExpired
      ? `<div style="display:flex;gap:6px">
          <button class="btn green sm" style="padding:4px 10px;font-size:12px" onclick="event.stopPropagation();confirmBloodBankForRequest('${reqId}','${mb.id}')">Confirm Blood Bank</button>
          <button class="btn danger sm" style="padding:4px 10px;font-size:12px" onclick="event.stopPropagation();rejectBloodBankForRequest('${reqId}','${mb.id}')">Reject</button>
        </div>`
      : "";

  return `<div class="bb-match-card" style="background:var(--card);border:1.5px solid var(--border);border-radius:12px;padding:14px;margin-bottom:10px;box-shadow:var(--sh)">
    <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:10px;margin-bottom:6px">
      <div>
        <div style="font-weight:800;font-size:15px;font-family:var(--display);color:var(--text)">🏦 ${esc(bb.name || "Blood Bank")}</div>
        ${bb.registrationNumber ? `<div style="font-size:11.5px;color:var(--text-m)">Reg / License No: ${esc(bb.registrationNumber)}</div>` : ""}
      </div>
      <div style="display:flex;flex-direction:column;align-items:flex-end;gap:4px">
        <span class="badge ${badgeClass}">${badgeLabel}</span>
        ${respBadge}
      </div>
    </div>
    <div style="font-size:12.5px;color:var(--text-m);margin-bottom:8px;display:flex;flex-wrap:wrap;gap:10px">
      <span>📍 ${mb.distance} km away</span>
      ${locStr ? `<span>📍 ${locStr}</span>` : ""}
      ${
        mb.compatibleStock && Object.keys(mb.compatibleStock).length > 0
          ? `<span>🩸 <strong>Compatible stock:</strong> ${mb.availableUnitsAtMatch} unit(s) (${esc(
              Object.entries(mb.compatibleStock)
                .map(([grp, qty]) => `${grp}: ${qty}`)
                .join(", "),
            )})</span>`
          : `<span>🩸 <strong>${esc(mb.bloodGroup)}</strong> — <strong>${mb.availableUnitsAtMatch}</strong> unit(s) available</span>`
      }
    </div>
    <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
      ${bb.phone ? `<div><a href="tel:${esc(bb.phone)}" class="btn green sm" style="text-decoration:none">📞 Call Blood Bank (${esc(bb.phone)})</a></div>` : "<div></div>"}
      ${actionBtns}
    </div>
  </div>`;
}

function showMatchResult(req, matchedBanks = []) {
  const totalBanks = matchedBanks.length;
  const bestBank = totalBanks > 0 ? matchedBanks[0] : null;

  let html = `<div style="text-align:center;padding:16px 0">
    <div style="font-size:56px;margin-bottom:8px">${totalBanks > 0 ? "🏦" : "⚠️"}</div>
    <div style="font-family:var(--display);font-size:22px;font-weight:800;margin-bottom:6px">${totalBanks > 0 ? "Blood Bank Priority Match" : "No Matching Blood Bank"}</div>
    <div style="color:var(--text-m);font-size:14px">${totalBanks > 0 ? `Blood request sent to <strong>${esc(bestBank ? bestBank.name : "Blood Bank")}</strong>. Donors are on standby.` : `No registered Blood Bank has ${esc(req.bloodGroup)} stock within ${req.radius}km.`}</div>
  </div>`;

  if (totalBanks > 0) {
    html += `<div style="font-weight:700;font-size:14px;margin:18px 0 8px">🏦 Eligible Blood Banks (${totalBanks})</div>`;
    html += matchedBanks.map((mb) => renderMatchedBankCard(mb)).join("");
    html += `<div style="font-size:12.5px;color:var(--text-m);padding:12px;background:var(--border-l);border-radius:10px;margin-top:14px">
      ℹ️ <strong>Blood Bank Escalation Policy:</strong> BloodLink notifies Blood Banks sequentially to fulfill hospital requirements before contacting individual donors.
    </div>`;
  } else {
    html += `<div style="font-size:13.5px;color:var(--cr);padding:14px;background:var(--border-l);border-radius:10px;margin:14px 0;font-weight:600">
      ⚠️ No registered Blood Bank currently has available stock of ${esc(req.bloodGroup)} within ${req.radius}km.
    </div>
    <button class="btn red block lg" style="margin-top:14px" onclick="alertDonorsForRequest('${req.id}');closeModal('modal-match')">
      🩸 Alert Donors Now
    </button>`;
  }

  $("match-content").innerHTML = html;
  openModal("modal-match");
  updateBadge();
}

/* MY REQUESTS */
function renderHRequests() {
  fetchHospitalRequests(CU.id).then((myR) => {
    const sorted = [...myR].sort(
      (a, b) => (parseServerDate(b.createdAt)?.getTime() || 0) - (parseServerDate(a.createdAt)?.getTime() || 0),
    );
    ["open", "fulfilled", "all"].forEach((s) => {
      const items = s === "all" ? sorted : sorted.filter((r) => r.status === s);
      const c = $("h-requests-" + s);
      if (!c) return;
      c.innerHTML = items.length
        ? items.map((r) => reqCard(r, false)).join("")
        : empty(
            { open: "🔴", fulfilled: "✅", all: "📋" }[s],
            "No " + s + " requests",
            "",
          );
    });
  });
}

function reqCard(r, mini) {
  const secured = r.securedUnits || 0;
  const total = r.units || 1;
  const pct = Math.min(100, Math.round((secured / total) * 100));
  const bankCnt = (r.bloodBanks || []).length;
  const isFulfilled = r.status === "fulfilled" || secured >= total;
  const isPartial =
    (secured > 0 && secured < total) || r.status === "partially_fulfilled";
  const isExpired = r.status === "expired" || r.isExpired;
  const isSched = r.requestType === "SCHEDULED";
  const pendingBank = (r.bloodBanks || []).find(
    (b) => b.responseStatus === "pending",
  );
  const allBanksDeclined =
    bankCnt > 0 && r.bloodBanks.every((b) => b.responseStatus === "rejected");

  let statusBadge = `<span class="badge red">🔴 Open</span>`;
  if (isFulfilled) {
    statusBadge = `<span class="badge green">🟢 Fulfilled</span>`;
  } else if (isExpired) {
    statusBadge = `<span class="badge grey">⚠️ Expired</span>`;
  } else if (isPartial) {
    statusBadge = `<span class="badge amber">🟡 Partially Fulfilled (${secured}/${total})</span>`;
  } else if (pendingBank) {
    statusBadge = `<span class="badge blue">⏳ Waiting for Blood Bank</span>`;
  } else if (allBanksDeclined) {
    statusBadge = `<span class="badge red">❌ Blood Bank Declined</span>`;
  } else if (bankCnt === 0) {
    statusBadge = `<span class="badge red">⚠️ No Blood Bank Available</span>`;
  }

  return `<div class="req-card ${esc(r.urgency)}" onclick="showReqDetail('${r.id}')">
    <div class="req-hdr">
      <div class="bgb md ${bgClass(r.bloodGroup)}">${esc(r.bloodGroup)}</div>
      <div style="flex:1;min-width:0">
        <div style="font-family:var(--display);font-size:15px;font-weight:800">
          Patient: ${esc(r.patientName)}
          ${isSched ? '<span class="badge blue sm" style="margin-left:6px">📅 SCHEDULED</span>' : ""}
        </div>
        <div class="dr-meta">🕐 ${ago(r.createdAt)} · 📍 ${r.radius}km radius</div>
        ${isSched && r.reason ? `<div style="font-size:12.5px;color:var(--text-m);margin-top:2px">🎯 <strong>Cause:</strong> ${esc(r.reason)}</div>` : ""}
      </div>
      <div style="display:flex;flex-direction:column;align-items:flex-end;gap:6px">
        ${!isSched ? `<span class="badge urg-${esc(r.urgency)}">${esc(r.urgency)}</span>` : `<span class="badge blue">📅 Scheduled</span>`}
        ${statusBadge}
      </div>
    </div>

    <!-- ACTUAL UNITS SECURED PROGRESS BAR -->
    <div style="margin:10px 0 4px">
      <div style="display:flex;justify-content:space-between;font-size:12.5px;font-weight:700;margin-bottom:4px;color:var(--text)">
        <span>Progress: <strong>${secured}/${total}</strong> units secured</span>
        <span>${pct}% complete</span>
      </div>
      <div style="height:10px;background:var(--border-l);border-radius:6px;overflow:hidden">
        <div style="width:${pct}%;background:${pct >= 100 ? "var(--gr)" : pct > 0 ? "var(--am)" : "var(--cr)"};height:100%"></div>
      </div>
    </div>

    ${
      isSched
        ? `<div style="font-size:12px;color:var(--text-m);background:var(--border-l);padding:8px 10px;border-radius:6px;margin:8px 0;display:flex;gap:12px;flex-wrap:wrap">
            <span>📅 <strong>Procedure:</strong> ${fmtDateTime(r.operationTime)}</span>
            <span>⏳ <strong>Deadline:</strong> ${fmtDateTime(r.accumulationDeadline)}</span>
          </div>`
        : ""
    }

    <div class="req-meta" style="margin-top:8px">
      <span>🩸 Requested: ${r.units} unit(s)</span>
      <span>📞 ${esc(r.contact)}</span>
      <span>🏦 ${bankCnt} Blood Bank(s) matched</span>
      <span>👥 ${r.donorAlertsSent ? (r.directDonors || []).length + (r.waitingDonors || []).length + " donors alerted" : "Donors on standby"}</span>
    </div>

    ${
      !mini
        ? `<div style="margin-top:12px;display:flex;gap:8px;flex-wrap:wrap" onclick="event.stopPropagation()">
      ${!isFulfilled && !isSched && !r.donorAlertsSent ? `<button class="btn red sm" onclick="alertDonorsForRequest('${r.id}')">🩸 Alert Donors (${total - secured} remaining)</button>` : ""}
      ${!isFulfilled ? `<button class="btn green sm" onclick="fulfillReq('${r.id}')">✅ Mark Fulfilled</button>` : ""}
    </div>`
        : ""
    }
  </div>`;
}

function renderPotentialMatchedDonors(r, matchRes) {
  const direct = (matchRes && matchRes.direct) || [];
  const waiting = (matchRes && matchRes.waiting) || [];
  const list = [...direct, ...waiting];

  if (!list.length) {
    return `<div style="font-weight:700;font-size:14px;margin:16px 0 6px">🩸 Potential Matched Donors</div>
      <div style="font-size:13.5px;color:var(--text-m);padding:12px;background:var(--border-l);border-radius:10px">
        No eligible donors found within the current radius.
      </div>`;
  }

  let html = `<div style="font-weight:700;font-size:14px;margin:16px 0 8px">🩸 Potential Matched Donors (${list.length} available)</div>`;
  html += list
    .slice(0, 5)
    .map((d) => {
      const fresh =
        d.freshnessStatus === "fresh"
          ? '<span class="badge green">🟢 Fresh</span>'
          : d.freshnessStatus === "stale"
            ? '<span class="badge amber">🟡 Stale</span>'
            : '<span class="badge grey">⚪ Old</span>';
      const distStr =
        d.distance != null
          ? `📍 ${d.distance.toFixed(1)}km away`
          : `📍 ${esc(d.city || "Unknown")}`;
      return `<div class="d-mini" style="margin-bottom:8px">
        <div class="av sm" style="background:var(--cr);color:#fff">${(d.name || "?")[0]}</div>
        <div class="d-mini-info" style="flex:1">
          <div class="d-mini-name">${esc(d.name)} <span class="badge sm red" style="margin-left:4px">${esc(d.bloodGroup)}</span></div>
          <div class="d-mini-meta">${distStr} · ✅ Available · ${fresh}</div>
        </div>
      </div>`;
    })
    .join("");

  if (list.length > 5) {
    html += `<div style="font-size:12px;color:var(--text-m);text-align:center;margin-top:4px">+ ${list.length - 5} more eligible donor(s) in radius</div>`;
  }

  return html;
}

async function showReqDetail(id) {
  let r = hospitalRequestsCache.find((x) => x.id === id);
  try {
    const res = await apiFetch(
      `${API_BASE}/api/requests/${encodeURIComponent(id)}`,
    );
    if (res.ok) {
      const fresh = await res.json();
      const idx = hospitalRequestsCache.findIndex((x) => x.id === id);
      if (idx >= 0) hospitalRequestsCache[idx] = fresh;
      else hospitalRequestsCache.unshift(fresh);
      r = fresh;
    }
  } catch (e) {}
  if (!r) return;
  const secured = r.securedUnits || 0;
  const total = r.units || 1;
  const remaining = Math.max(0, total - secured);
  const pct = Math.min(100, Math.round((secured / total) * 100));
  const isFulfilled = r.status === "fulfilled" || secured >= total;
  const isExpired = r.status === "expired" || r.isExpired;
  const isSched = r.requestType === "SCHEDULED";

  let potentialDonorMatches = null;
  if (!r.donorAlertsSent) {
    try {
      potentialDonorMatches = await matchDonorsForBloodRequest(r.id);
    } catch (e) {
      potentialDonorMatches = null;
    }
  }

  const allDonors = [...(r.directDonors || []), ...(r.waitingDonors || [])];
  const sentTo = allDonors.length;
  const acceptedCnt = allDonors.filter((d) => d.status === "accepted").length;
  const confirmedCnt = allDonors.filter((d) => d.status === "confirmed").length;
  const rejectedCnt = allDonors.filter(
    (d) => d.status === "rejected" || d.status === "declined",
  ).length;
  const notRespondedCnt = allDonors.filter(
    (d) => d.status === "pending",
  ).length;

  const donorSummaryHtml =
    r.donorAlertsSent || allDonors.length > 0
      ? `<div style="background:var(--border-l);border-radius:10px;padding:14px;margin-bottom:14px">
         <div style="font-weight:700;font-size:14px;margin-bottom:10px">📊 Donor Response Summary</div>
         <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(85px, 1fr));gap:8px;text-align:center">
           <div style="background:var(--card);padding:8px 6px;border-radius:8px">
             <div style="font-size:11px;color:var(--text-m);font-weight:600">Sent To</div>
             <div style="font-size:16px;font-weight:800">${sentTo}</div>
           </div>
           <div style="background:var(--card);padding:8px 6px;border-radius:8px">
             <div style="font-size:11px;color:var(--am);font-weight:600">Accepted</div>
             <div style="font-size:16px;font-weight:800;color:var(--am)">${acceptedCnt}</div>
           </div>
           <div style="background:var(--card);padding:8px 6px;border-radius:8px">
             <div style="font-size:11px;color:var(--gr);font-weight:600">Confirmed</div>
             <div style="font-size:16px;font-weight:800;color:var(--gr)">${confirmedCnt}</div>
           </div>
           <div style="background:var(--card);padding:8px 6px;border-radius:8px">
             <div style="font-size:11px;color:var(--cr);font-weight:600">Rejected</div>
             <div style="font-size:16px;font-weight:800;color:var(--cr)">${rejectedCnt}</div>
           </div>
           <div style="background:var(--card);padding:8px 6px;border-radius:8px">
             <div style="font-size:11px;color:var(--text-m);font-weight:600">Not Responded</div>
             <div style="font-size:16px;font-weight:800">${notRespondedCnt}</div>
           </div>
         </div>
       </div>`
      : "";

  const dList = (arr, label) =>
    arr.length
      ? `<div style="font-weight:700;font-size:14px;margin:14px 0 8px">${label} (${arr.length})</div>` +
        arr
          .map((dd) => {
            const d = dd;
            const isAccepted = dd.status === "accepted";
            const isConfirmed = dd.status === "confirmed";
            const isDeclined =
              dd.status === "rejected" || dd.status === "declined";
            const statusBadge = isConfirmed
              ? `<span class="badge green">✅ Confirmed</span>`
              : isAccepted
                ? `<span class="badge amber">🟡 Accepted</span>`
                : isDeclined
                  ? `<span class="badge grey">❌ Declined</span>`
                  : `<span class="badge blue">⏳ Pending</span>`;

            const actionBtn =
              isAccepted && !isFulfilled && !isExpired
                ? `<button class="btn green sm" style="margin-left:6px;padding:3px 8px;font-size:11.5px" onclick="event.stopPropagation();confirmDonorForRequest('${r.id}','${dd.id}')">Confirm Donor</button>`
                : "";

            return `<div class="d-mini" style="display:flex;align-items:center;gap:10px;padding:10px;background:var(--card);border-radius:8px;margin-bottom:8px;border:1px solid var(--border)">
              <div class="av sm" style="background:${isConfirmed ? "var(--gr)" : isAccepted ? "var(--am)" : isDeclined ? "var(--text-l)" : "var(--cr)"}">${(d.name || "?")[0]}</div>
              <div class="d-mini-info" style="flex:1;min-width:0">
                <div class="d-mini-name" style="font-weight:700">${esc(d.name)} <span class="badge sm red" style="margin-left:4px">${esc(d.bloodGroup || r.bloodGroup)}</span></div>
                <div class="d-mini-meta" style="font-size:12px;color:var(--text-m)">📞 ${esc(d.phone || "N/A")} ${d.city ? "· 📍 " + esc(d.city) : ""}</div>
              </div>
              <div style="display:flex;align-items:center;gap:4px">
                ${statusBadge}
                ${actionBtn}
              </div>
            </div>`;
          })
          .join("")
      : "";

  const bList = (banks) => {
    if (CU.lat == null || CU.lng == null) {
      return `<div style="font-weight:700;font-size:14px;margin:16px 0 8px">🏦 Matching Blood Banks</div><div style="font-size:13px;color:var(--text-m);padding:12px;background:var(--border-l);border-radius:10px">📍 Blood Bank distance matching requires a valid hospital location. Please update your hospital GPS location.</div>`;
    }
    if (!banks || !banks.length) {
      return `<div style="font-weight:700;font-size:14px;margin:16px 0 8px">🏦 Matching Blood Banks</div><div style="font-size:13.5px;color:var(--cr);padding:12px;background:var(--border-l);border-radius:10px">⚠️ No registered Blood Bank within ${r.radius}km currently has ${esc(r.bloodGroup)} stock.</div>`;
    }
    return (
      `<div style="font-weight:700;font-size:14px;margin:16px 0 8px">🏦 Matching Blood Banks (${banks.length})</div>` +
      banks
        .map((mb) => renderMatchedBankCard(mb, r.id, isFulfilled, isExpired))
        .join("")
    );
  };

  $("req-detail").innerHTML = `
    <div style="display:flex;align-items:center;gap:14px;margin-bottom:18px;flex-wrap:wrap">
      <div class="bgb lg ${bgClass(r.bloodGroup)}">${esc(r.bloodGroup)}</div>
      <div>
        <div style="font-family:var(--display);font-size:18px;font-weight:800">
          Patient: ${esc(r.patientName)}
          ${isSched ? '<span class="badge blue sm" style="margin-left:6px">📅 SCHEDULED</span>' : ""}
        </div>
        <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:8px">
          ${!isSched ? `<span class="badge urg-${esc(r.urgency)}">${esc(r.urgency)}</span>` : `<span class="badge blue">📅 Scheduled</span>`}
          <span class="badge ${isFulfilled ? "green" : isExpired ? "grey" : secured > 0 ? "amber" : "red"}">${isFulfilled ? "🟢 Fulfilled" : isExpired ? "⚠️ Expired" : secured > 0 ? "🟡 Partially Fulfilled" : "🔴 Open"}</span>
          <span class="badge blue">${secured}/${total} units secured (${pct}%)</span>
        </div>
      </div>
    </div>

    <!-- PROGRESS BAR IN DETAIL MODAL -->
    <div style="background:var(--border-l);border-radius:10px;padding:14px;margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;font-size:13px;font-weight:700;margin-bottom:6px">
        <span>Units Secured: ${secured} of ${total} required</span>
        <span>${pct}% complete</span>
      </div>
      <div style="height:10px;background:var(--card);border-radius:6px;overflow:hidden">
        <div style="width:${pct}%;background:${pct >= 100 ? "var(--gr)" : pct > 0 ? "var(--am)" : "var(--cr)"};height:100%"></div>
      </div>
    </div>

    ${
      isSched
        ? `<div style="background:var(--border-l);border-radius:10px;padding:12px;margin-bottom:14px;font-size:13px">
            ${r.reason ? `<div style="margin-bottom:4px">🎯 <strong>Clinical Cause:</strong> ${esc(r.reason)}</div>` : ""}
            <div style="margin-bottom:4px">📅 <strong>Operation / Requirement Time:</strong> ${fmtDateTime(r.operationTime)}</div>
            <div>⏳ <strong>Accumulation Deadline:</strong> ${fmtDateTime(r.accumulationDeadline)}</div>
          </div>`
        : ""
    }

    <div style="background:var(--border-l);border-radius:10px;padding:12px;margin-bottom:14px;font-size:13.5px">
      <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
        <div>📞 ${esc(r.contact)} &nbsp;·&nbsp; 📅 ${fmt(r.createdAt)} &nbsp;·&nbsp; 📍 <strong>${r.radius}km</strong> search radius</div>
        ${
          !isFulfilled && !isExpired
            ? r.radius < 50
              ? `<button class="btn blue sm" style="padding:4px 10px;font-size:12px" onclick="increaseRequestRadius('${r.id}')">📍 Increase Radius</button>`
              : `<button class="btn ghost sm" style="padding:4px 10px;font-size:12px" disabled>📍 Max Radius (50km)</button>`
            : ""
        }
      </div>
      ${r.notes ? `<div style="margin-top:4px;color:var(--text-m);font-style:italic">💬 ${esc(r.notes)}</div>` : ""}
    </div>

    ${donorSummaryHtml}

    ${bList(r.bloodBanks || [])}

    ${r.donorAlertsSent ? dList(r.directDonors || [], "🎯 Direct Donor Contacts") + dList(r.waitingDonors || [], "📋 Donor Standby List") : renderPotentialMatchedDonors(r, potentialDonorMatches)}

    <div style="margin-top:18px;display:flex;flex-direction:column;gap:10px">
      ${!isFulfilled && !isSched && remaining > 0 ? `<button class="btn red block lg" onclick="alertDonorsForRequest('${r.id}');closeModal('modal-req')">🩸 Alert Donors (${remaining} remaining unit(s))</button>` : ""}
      ${!isFulfilled ? `<button class="btn green block lg" onclick="fulfillReq('${r.id}');closeModal('modal-req')">✅ Mark as Fulfilled</button>` : ""}
    </div>`;
  openModal("modal-req");
}

function confirmDonorForRequest(reqId, donorId) {
  if (!CU || CU.role !== "hospital") return;

  if (
    !confirm(
      "Have you contacted and verified this donor?\n\nConfirming this donor will count 1 unit toward the request.",
    )
  ) {
    return;
  }

  apiFetch(
    `${API_BASE}/api/requests/${encodeURIComponent(reqId)}/donors/${encodeURIComponent(donorId)}/confirm`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ hospitalId: CU.id }),
    },
  )
    .then(async (res) => {
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        toast(data.error || "Failed to confirm donor.", "err");
        return;
      }
      toast("Donor confirmed successfully! Secured 1 unit. 🩸", "ok");

      fetchHospitalRequests().then(() => {
        renderHRequests();
        renderHDash();
        showReqDetail(reqId);
      });
    })
    .catch(() => {
      toast("Failed to confirm donor. Please check backend connection.", "err");
    });
}

function confirmBloodBankForRequest(reqId, bloodBankId) {
  if (!CU || CU.role !== "hospital") return;

  if (
    !confirm(
      "Confirm blood bank request fulfillment?\n\nThis will confirm securing units from this blood bank.",
    )
  ) {
    return;
  }

  apiFetch(
    `${API_BASE}/api/requests/${encodeURIComponent(reqId)}/bloodbanks/${encodeURIComponent(bloodBankId)}/confirm`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ hospitalId: CU.id }),
    },
  )
    .then(async (res) => {
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        toast(data.error || "Failed to confirm blood bank.", "err");
        return;
      }
      toast("Blood bank confirmed successfully! 🏦", "ok");

      fetchHospitalRequests().then(() => {
        renderHRequests();
        renderHDash();
        showReqDetail(reqId);
      });
    })
    .catch(() => {
      toast(
        "Failed to confirm blood bank. Please check backend connection.",
        "err",
      );
    });
}

function rejectBloodBankForRequest(reqId, bloodBankId) {
  if (!CU || CU.role !== "hospital") return;

  if (
    !confirm(
      "Are you sure you want to reject this blood bank's response?\n\nThe next queued blood bank or donors will be prioritized.",
    )
  ) {
    return;
  }

  apiFetch(
    `${API_BASE}/api/requests/${encodeURIComponent(reqId)}/bloodbanks/${encodeURIComponent(bloodBankId)}/reject`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ hospitalId: CU.id }),
    },
  )
    .then(async (res) => {
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        toast(data.error || "Failed to reject blood bank.", "err");
        return;
      }
      toast("Blood bank response rejected.", "info");

      fetchHospitalRequests().then(() => {
        renderHRequests();
        renderHDash();
        showReqDetail(reqId);
      });
    })
    .catch(() => {
      toast(
        "Failed to reject blood bank. Please check backend connection.",
        "err",
      );
    });
}

function fulfillReq(id) {
  if (!CU || CU.role !== "hospital") return;

  apiFetch(`${API_BASE}/api/requests/${encodeURIComponent(id)}/fulfill`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ hospitalId: CU.id }),
  })
    .then(async (res) => {
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        toast(data.error || "Failed to mark request as fulfilled.", "err");
        return;
      }
      const data = await res.json();
      const r = hospitalRequestsCache.find((x) => x.id === id);
      if (r) {
        r.status = "fulfilled";
        r.securedUnits = r.units;
        r.fulfilledAt = data.fulfilledAt || new Date().toISOString();
      }
      toast("Request marked as fulfilled ✅", "ok");
      if ($("scr-app") && $("scr-app").style.display !== "none") {
        fetchHospitalRequests().then(() => {
          renderHRequests();
          renderHDash();
        });
      }
    })
    .catch(() => {
      toast(
        "Failed to fulfill request. Please check backend connection.",
        "err",
      );
    });
}

/* TOOLS */
function renderHTools() {
  renderCompat("h");
  renderHospGuide();
  renderFAQ("h");
}
function renderHospGuide() {
  const g = $("hosp-guide");
  if (!g || g.children.length) return;
  g.innerHTML = `<div class="guide-card"><div class="ctitle">🏥 How BloodLink Works for Hospitals</div>${[
    "Register with accurate GPS for precise matching.",
    'Go to "New Request", specify blood group, units, urgency, and radius.',
    "Algorithm finds compatible donors in your chosen radius.",
    "Top 3 closest donors directly contacted.",
    "Next 7 form a standby list.",
    'Track responses in "My Requests".',
    "Mark request as Fulfilled once done.",
  ]
    .map(
      (s, i) =>
        `<div class="g-step"><div class="g-num b">${i + 1}</div><div><p>${s}</p></div></div>`,
    )
    .join("")}</div>`;
}

/* HOSPITAL PROFILE */
function renderHProfile() {
  if (!CU) return;
  $("hp-name-d").textContent = CU.name;
  $("hp-email-d").textContent = CU.email;
  sv("hp-name", CU.name);
  sv("hp-phone", CU.phone || "");
  sv("hp-city", CU.city || "");
  sv("hp-state", CU.state || "");
  sv("hp-lat", CU.lat || "");
  sv("hp-lng", CU.lng || "");
  sv("hp-reg", CU.registrationNumber || "");
  updateLocBadge("hp");
}
function saveHospProfile() {
  CU.name = gv("hp-name");
  CU.phone = gv("hp-phone");
  CU.city = gv("hp-city");
  CU.state = gv("hp-state");
  CU.lat = +gv("hp-lat") || null;
  CU.lng = +gv("hp-lng") || null;
  CU.registrationNumber = gv("hp-reg");
  saveUser();
  updateSidebar();
  renderHRequest();
  toast("Profile updated ✅", "ok");
  renderHProfile();
}

/* BLOOD BANK PROFILE */
function renderBProfile() {
  if (!CU) return;
  const nameEl = $("bp-name-d");
  const emailEl = $("bp-email-d");
  if (nameEl) nameEl.textContent = CU.name || "—";
  if (emailEl) emailEl.textContent = CU.email || "—";
  sv("bp-name", CU.name || "");
  sv("bp-phone", CU.phone || "");
  sv("bp-city", CU.city || "");
  sv("bp-state", CU.state || "");
  sv("bp-lat", CU.lat || "");
  sv("bp-lng", CU.lng || "");
  sv("bp-reg", CU.registrationNumber || "");
  sv("bp-hours", CU.operatingHours || "");
  updateLocBadge("bp");
}

function saveBankProfile() {
  CU.name = gv("bp-name");
  CU.phone = gv("bp-phone");
  CU.city = gv("bp-city");
  CU.state = gv("bp-state");
  CU.lat = +gv("bp-lat") || null;
  CU.lng = +gv("bp-lng") || null;
  CU.registrationNumber = gv("bp-reg");
  CU.operatingHours = gv("bp-hours");
  delete CU.address;
  saveUser();
  updateSidebar();
  renderBDash();
  toast("Profile updated ✅", "ok");
  renderBProfile();
}

/* ═════════════════════════════════════════════
   BLOOD BANK PAGES
═════════════════════════════════════════════ */
function openAddStockModal(group) {
  clrE("b-add-bg");
  clrE("b-add-units");
  sv("b-add-bg", group || "");
  sv("b-add-units", "");
  openModal("modal-b-add-stock");
}

function doAddStock() {
  if (!CU || CU.role !== "bloodbank") return;
  clrE("b-add-bg");
  clrE("b-add-units");
  const bg = gv("b-add-bg");
  const rawUnits = gv("b-add-units");
  const units = Number(rawUnits);

  let ok = true;
  if (!bg || !BB_GROUPS.includes(bg)) {
    setE("b-add-bg", "Blood group is required");
    ok = false;
  }
  if (!rawUnits || isNaN(units) || !Number.isInteger(units) || units <= 0) {
    setE("b-add-units", "Units must be a positive whole number (> 0)");
    ok = false;
  }
  if (!ok) return;

  const inv = getBloodBankInventory(CU.id);
  const currentStock = inv.stock[bg] || 0;
  const newStock = { ...inv.stock, [bg]: currentStock + units };
  const newLastUpdated = { ...inv.lastUpdated, [bg]: new Date().toISOString() };

  saveBloodBankInventory(CU.id, newStock, newLastUpdated)
    .then(() => {
      closeModal("modal-b-add-stock");
      toast(`Added ${units} unit(s) to ${bg} stock`, "ok");
      renderBDash();
    })
    .catch(() => {});
}

function openUpdateStockModal(group) {
  clrE("b-up-bg");
  clrE("b-up-units");
  sv("b-up-bg", group || "");
  if (group && CU) {
    const inv = getBloodBankInventory(CU.id);
    sv("b-up-units", inv.stock[group] !== undefined ? inv.stock[group] : 0);
  } else {
    sv("b-up-units", "");
  }
  openModal("modal-b-update-stock");
}

function onUpdateBgChange() {
  const bg = gv("b-up-bg");
  if (bg && CU) {
    const inv = getBloodBankInventory(CU.id);
    sv("b-up-units", inv.stock[bg] !== undefined ? inv.stock[bg] : 0);
  }
}

function doUpdateStock() {
  if (!CU || CU.role !== "bloodbank") return;
  clrE("b-up-bg");
  clrE("b-up-units");
  const bg = gv("b-up-bg");
  const rawUnits = gv("b-up-units");
  const units = Number(rawUnits);

  let ok = true;
  if (!bg || !BB_GROUPS.includes(bg)) {
    setE("b-up-bg", "Blood group is required");
    ok = false;
  }
  if (
    rawUnits === "" ||
    isNaN(units) ||
    !Number.isInteger(units) ||
    units < 0
  ) {
    setE("b-up-units", "Units must be a non-negative whole number (≥ 0)");
    ok = false;
  }
  if (!ok) return;

  const inv = getBloodBankInventory(CU.id);
  const newStock = { ...inv.stock, [bg]: units };
  const newLastUpdated = { ...inv.lastUpdated, [bg]: new Date().toISOString() };

  saveBloodBankInventory(CU.id, newStock, newLastUpdated)
    .then(() => {
      closeModal("modal-b-update-stock");
      toast(`Updated ${bg} stock to ${units} unit(s)`, "ok");
      renderBDash();
    })
    .catch(() => {});
}

function confirmRemoveStock(group) {
  if (!CU || CU.role !== "bloodbank" || !BB_GROUPS.includes(group)) return;
  const msg = $("b-remove-msg");
  if (msg) {
    msg.innerHTML = `Are you sure you want to remove all <strong>${esc(group)}</strong> stock?`;
  }
  const btn = $("b-remove-confirm-btn");
  if (btn) {
    btn.onclick = () => doRemoveStock(group);
  }
  openModal("modal-b-remove-stock");
}

function doRemoveStock(group) {
  if (!CU || CU.role !== "bloodbank" || !BB_GROUPS.includes(group)) return;
  const inv = getBloodBankInventory(CU.id);
  const newStock = { ...inv.stock, [group]: 0 };
  const newLastUpdated = {
    ...inv.lastUpdated,
    [group]: new Date().toISOString(),
  };

  saveBloodBankInventory(CU.id, newStock, newLastUpdated)
    .then(() => {
      closeModal("modal-b-remove-stock");
      toast(`All ${group} stock removed (0 units)`, "info");
      renderBDash();
    })
    .catch(() => {});
}

function renderBDash() {
  if (!CU || CU.role !== "bloodbank") return;
  $("b-wname").textContent = CU.name || "Blood Bank";
  const bWaddrEl = $("b-waddr");
  if (bWaddrEl) {
    bWaddrEl.textContent = CU.city
      ? "📍 " + CU.city + (CU.state ? ", " + CU.state : "")
      : "📍 Location not set";
  }

  Promise.all([
    fetchBloodBankInventory(CU.id),
    fetchBloodBankAlerts(CU.id),
  ]).then(([inv, allAlerts]) => {
    const stock = inv.stock || emptyBloodStock();
    const lastUpdated = inv.lastUpdated || emptyLastUpdated();

    const reservedByGroup = {
      ...emptyBloodStock(),
      ...(inv.reservedStock || {}),
    };
    if (!inv.reservedStock || Object.keys(inv.reservedStock).length === 0) {
      (allAlerts || []).forEach((a) => {
        if (a.bankStatus === "accepted") {
          const res =
            a.bankMatch && a.bankMatch.reservedUnits !== undefined
              ? a.bankMatch.reservedUnits
              : (a.bankMatch && a.bankMatch.unitsSecured) || 0;
          const bg = a.bloodGroup;
          if (bg && reservedByGroup[bg] !== undefined) {
            reservedByGroup[bg] += res;
          }
        }
      });
    }

    let totalUnits = 0;
    let totalReservedUnits = 0;
    let availableCnt = 0;
    let lowCnt = 0;
    let noStockCnt = 0;

    BB_GROUPS.forEach((g) => {
      const u = stock[g] || 0;
      const res = reservedByGroup[g] || 0;
      totalUnits += u;
      totalReservedUnits += res;
      if (u <= 0) noStockCnt++;
      else if (u <= 5) lowCnt++;
      else availableCnt++;
    });

    const pendCnt = (allAlerts || []).filter(
      (a) => a.bankStatus === "pending",
    ).length;

    const statsEl = $("b-stats");
    if (statsEl) {
      statsEl.innerHTML = `
        <div class="sc" onclick="showPage('b-alerts')" style="cursor:pointer"><div class="sc-n a">${pendCnt}</div><div class="sc-l">Pending Requests</div></div>
        <div class="sc"><div class="sc-n r">${totalUnits}</div><div class="sc-l">Total Stock (Units)</div></div>
        <div class="sc"><div class="sc-n g">${availableCnt}</div><div class="sc-l">Available Groups</div></div>
        <div class="sc"><div class="sc-n">${noStockCnt}</div><div class="sc-l">No Stock Groups</div></div>
        <div class="sc"><div class="sc-n a">${totalReservedUnits}</div><div class="sc-l">Reserved Units</div></div>`;
    }

    const tableEl = $("b-inventory-table");
    if (tableEl) {
      let html = `<div class="tbl-wrap"><table>
        <thead>
          <tr>
            <th>Blood Group</th>
            <th>Available Units</th>
            <th>Reserved Units</th>
            <th>Status</th>
            <th>Last Updated</th>
            <th style="text-align:right">Actions</th>
          </tr>
        </thead>
        <tbody>`;

      BB_GROUPS.forEach((g) => {
        const u = stock[g] || 0;
        const res = reservedByGroup[g] || 0;
        const st = calcStockStatus(u);
        const lu = fmtDateTime(lastUpdated[g]);

        html += `<tr>
          <td><div class="bgb sm ${bgClass(g)}">${g}</div></td>
          <td><strong style="font-size:15px">${u}</strong> <span style="font-size:12px;color:var(--text-m)">unit(s)</span></td>
          <td><strong style="font-size:15px;${res > 0 ? "color:var(--am)" : ""}">${res}</strong> <span style="font-size:12px;color:var(--text-m)">unit(s)</span></td>
          <td><span class="badge ${st.badgeClass}">${st.label}</span></td>
          <td style="color:var(--text-m);font-size:13px">${lu}</td>
          <td style="text-align:right">
            <div style="display:inline-flex;gap:6px;justify-content:flex-end">
              <button class="btn green sm" onclick="openAddStockModal('${g}')" title="Add units">+ Add</button>
              <button class="btn ghost sm" onclick="openUpdateStockModal('${g}')" title="Set exact stock">✏️ Edit</button>
              ${u > 0 ? `<button class="btn danger sm" onclick="confirmRemoveStock('${g}')" title="Remove all stock">🗑️ Remove</button>` : `<button class="btn ghost sm" disabled title="No stock to remove" style="opacity:.4">🗑️ Remove</button>`}
            </div>
          </td>
        </tr>`;
      });

      html += `</tbody></table></div>`;
      tableEl.innerHTML = html;
    }
  });

  const loc =
    CU.lat != null && CU.lng != null
      ? `${Number(CU.lat).toFixed(4)}, ${Number(CU.lng).toFixed(4)}`
      : "Not captured";
  const cityState = CU.city
    ? `${esc(CU.city)}${CU.state ? ", " + esc(CU.state) : ""}`
    : "—";
  $("b-dash-details").innerHTML = `
    <div class="frow">
      <div class="fg" style="margin-bottom:8px"><label class="lbl">Registration / License ID</label><div>${esc(CU.registrationNumber || "—")}</div></div>
      <div class="fg" style="margin-bottom:8px"><label class="lbl">Email</label><div>${esc(CU.email || "—")}</div></div>
    </div>
    <div class="frow">
      <div class="fg" style="margin-bottom:8px"><label class="lbl">Contact Number</label><div>${esc(CU.phone || "—")}</div></div>
      <div class="fg" style="margin-bottom:8px"><label class="lbl">Operating Hours</label><div>${esc(CU.operatingHours || "—")}</div></div>
    </div>
    <div class="frow">
      <div class="fg" style="margin-bottom:8px"><label class="lbl">City / State</label><div>${cityState}</div></div>
      <div class="fg" style="margin-bottom:8px"><label class="lbl">Location (GPS)</label><div>${esc(loc)}</div></div>
    </div>`;
}

/* ── BLOOD BANK ALERTS & REQUEST RESPONSES (MYSQL-BACKED) ── */
let bloodBankAlertsCache = [];

function fetchBloodBankAlerts(bloodBankId) {
  if (!CU || CU.role !== "bloodbank") return Promise.resolve([]);
  const bbId = bloodBankId || CU.id;
  return apiFetch(
    `${API_BASE}/api/bloodbanks/${encodeURIComponent(bbId)}/alerts`,
  )
    .then(async (res) => {
      if (!res.ok) {
        return bloodBankAlertsCache;
      }
      const data = await res.json();
      bloodBankAlertsCache = Array.isArray(data) ? data : [];
      return bloodBankAlertsCache;
    })
    .catch(() => bloodBankAlertsCache);
}

function myBloodBankAlerts() {
  if (!CU || CU.role !== "bloodbank") return [];
  return bloodBankAlertsCache || [];
}

function renderBAlerts() {
  if (!CU || CU.role !== "bloodbank") return;
  fetchBloodBankAlerts(CU.id).then((all) => {
    const pendCnt = all.filter((a) => a.bankStatus === "pending").length;
    const c = $("b-alert-cnt");
    if (c) {
      c.textContent = pendCnt > 0 ? `${pendCnt} pending` : "";
      c.style.display = pendCnt > 0 ? "" : "none";
    }
    ["pending", "accepted", "rejected", "all"].forEach((s) => {
      const items =
        s === "all"
          ? all
          : s === "pending"
            ? all.filter((a) => a.bankStatus === "pending")
            : all.filter(
                (a) => a.bankStatus === (s === "rejected" ? "rejected" : s),
              );
      const el = $("b-alerts-" + s);
      if (!el) return;
      el.innerHTML = items.length
        ? items
            .map((a) =>
              bloodBankAlertCard(
                a,
                a.bankStatus === "pending" &&
                  a.status !== "fulfilled" &&
                  a.bankStatus !== "closed",
              ),
            )
            .join("")
        : empty(
            { pending: "🔔", accepted: "✅", rejected: "❌", all: "📋" }[s],
            s === "pending"
              ? "No pending blood requests"
              : "No " + s + " requests",
            s === "pending"
              ? "Requests from nearby hospitals will appear here when your inventory matches their search radius."
              : "",
          );
    });
    updateBadge();
  });
}

function bloodBankAlertCard(a, showActs) {
  const h = {
    name: a.hospitalName || "Hospital",
    city: a.hospitalCity || "",
    phone: a.hospitalPhone || a.contact,
  };
  const bm = a.bankMatch || {};
  const ui = { Normal: "🔵", Urgent: "🟠", Critical: "🔴" };
  const isSched = a.requestType === "SCHEDULED";
  const isFulfilled =
    a.status === "fulfilled" ||
    a.bankStatus === "closed" ||
    bm.responseStatus === "closed";
  const suffBadge =
    bm.status === "sufficient"
      ? `<span class="badge green">🟢 Sufficient</span>`
      : `<span class="badge amber">🟡 Partial Availability</span>`;
  const respBadge =
    a.bankStatus === "confirmed"
      ? `<span class="badge green">✅ Confirmed by Hospital</span>`
      : a.bankStatus === "accepted"
        ? `<span class="badge amber">🟡 Accepted (Awaiting Confirmation)</span>`
        : a.bankStatus === "rejected"
          ? `<span class="badge grey">❌ Declined</span>`
          : isFulfilled
            ? `<span class="badge green">✓ Fulfilled</span>`
            : `<span class="badge blue">⏳ Pending Response</span>`;

  return `<div class="alert-card ${esc(a.urgency)}" style="cursor:pointer" onclick="showBankReqDetail('${a.id}')">
    <div class="alert-hdr">
      <div class="bgb md ${bgClass(a.bloodGroup)}">${esc(a.bloodGroup)}</div>
      <div style="flex:1;min-width:0">
        <div style="font-weight:800;font-size:15px;font-family:var(--display)">
          🏥 ${esc(h.name || "Hospital")}
          ${isSched ? '<span class="badge blue sm" style="margin-left:6px">📅 SCHEDULED</span>' : ""}
        </div>
        <div class="dr-meta">📍 ${esc(h.city || "")} · Patient: <strong>${esc(a.patientName)}</strong></div>
        ${isSched && a.reason ? `<div style="font-size:12.5px;color:var(--text-m);margin-top:2px">🎯 <strong>Cause:</strong> ${esc(a.reason)}</div>` : ""}
      </div>
      <div style="display:flex;flex-direction:column;align-items:flex-end;gap:4px">
        ${!isSched ? `<span class="badge urg-${esc(a.urgency)}">${ui[a.urgency] || ""} ${esc(a.urgency)}</span>` : `<span class="badge blue">📅 Scheduled</span>`}
        ${respBadge}
      </div>
    </div>
    <div class="alert-meta">
      <span>🩸 ${a.units} unit(s) requested</span>
      <span>📍 ${bm.distance || 0} km away</span>
      <span>📦 Stock at match: ${bm.availableUnitsAtMatch !== undefined ? bm.availableUnitsAtMatch : 0} unit(s)</span>
      ${suffBadge}
      <span>🕐 ${ago(a.createdAt)}</span>
    </div>
    ${
      isSched
        ? `<div style="font-size:12px;color:var(--text-m);background:var(--border-l);padding:8px 10px;border-radius:6px;margin:6px 0;display:flex;gap:12px;flex-wrap:wrap">
            <span>📅 <strong>Procedure:</strong> ${fmtDateTime(a.operationTime)}</span>
            <span>⏳ <strong>Deadline:</strong> ${fmtDateTime(a.accumulationDeadline)}</span>
          </div>`
        : ""
    }
    ${a.notes ? `<div style="font-size:13px;color:var(--text-m);padding:10px;background:var(--border-l);border-radius:8px;margin-bottom:10px">💬 ${esc(a.notes)}</div>` : ""}
    ${
      showActs && !isFulfilled
        ? `<div class="alert-acts" onclick="event.stopPropagation()">
           <button class="btn green sm" onclick="respondBloodBankAlert('${a.id}','accepted')">✅ Accept Request</button>
           <button class="btn danger sm" onclick="respondBloodBankAlert('${a.id}','rejected')">❌ Decline</button>
         </div>`
        : ""
    }
  </div>`;
}

function showBankReqDetail(id) {
  let req = (bloodBankAlertsCache || []).find((x) => x.id === id);
  if (!req) return;

  const h = {
    name: req.hospitalName || "Hospital",
    city: req.hospitalCity || "",
    phone: req.hospitalPhone || req.contact,
  };
  const bm =
    req.bankMatch || (req.bloodBanks || []).find((b) => b.id === CU.id) || {};
  const isFulfilled =
    req.status === "fulfilled" ||
    req.bankStatus === "closed" ||
    bm.responseStatus === "closed";
  const isPending =
    !isFulfilled &&
    (req.bankStatus || bm.responseStatus || "pending") === "pending";
  const isSched = req.requestType === "SCHEDULED";
  const isAccepted = (req.bankStatus || bm.responseStatus) === "accepted";
  const isConfirmed = (req.bankStatus || bm.responseStatus) === "confirmed";
  const isDeclined = (req.bankStatus || bm.responseStatus) === "rejected";

  $("req-detail").innerHTML = `
    <div style="display:flex;align-items:center;gap:14px;margin-bottom:18px;flex-wrap:wrap">
      <div class="bgb lg ${bgClass(req.bloodGroup)}">${esc(req.bloodGroup)}</div>
      <div>
        <div style="font-family:var(--display);font-size:18px;font-weight:800">
          Hospital: ${esc(h.name || "Hospital")}
          ${isSched ? '<span class="badge blue sm" style="margin-left:6px">📅 SCHEDULED</span>' : ""}
        </div>
        <div style="font-size:14px;color:var(--text-m);margin-top:2px">Patient: <strong>${esc(req.patientName)}</strong></div>
        <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:8px">
          ${!isSched ? `<span class="badge urg-${esc(req.urgency)}">${esc(req.urgency)}</span>` : `<span class="badge blue">📅 Scheduled</span>`}
          <span class="badge ${bm.status === "sufficient" ? "green" : "amber"}">${bm.status === "sufficient" ? "🟢 Sufficient" : "🟡 Partial Availability"}</span>
          <span class="badge ${isConfirmed ? "green" : isAccepted ? "amber" : isDeclined ? "grey" : isFulfilled ? "green" : "blue"}">Response: ${isConfirmed ? `✅ Confirmed (${bm.unitsSecured || ""} units)` : isAccepted ? `🟡 Accepted (${bm.reservedUnits !== undefined ? bm.reservedUnits : bm.unitsSecured || ""} units reserved)` : isDeclined ? "❌ Declined" : isFulfilled ? "✓ Fulfilled" : "⏳ Pending"}</span>
        </div>
      </div>
    </div>
    <div style="background:var(--border-l);border-radius:10px;padding:14px;margin-bottom:14px;font-size:13.5px">
      ${isSched && req.reason ? `<div style="margin-bottom:6px">🎯 <strong>Clinical Cause:</strong> ${esc(req.reason)}</div>` : ""}
      ${isSched ? `<div style="margin-bottom:6px">📅 <strong>Operation Time:</strong> ${fmtDateTime(req.operationTime)}</div>` : ""}
      ${isSched ? `<div style="margin-bottom:6px">⏳ <strong>Accumulation Deadline:</strong> ${fmtDateTime(req.accumulationDeadline)}</div>` : ""}
      <div style="margin-bottom:6px">📞 <strong>Contact:</strong> ${esc(h.phone || req.contact || "N/A")}</div>
      <div style="margin-bottom:6px">📍 <strong>Distance:</strong> ${bm.distance || 0} km away</div>
      <div style="margin-bottom:6px">📦 <strong>Available Units At Match:</strong> ${bm.availableUnitsAtMatch !== undefined ? bm.availableUnitsAtMatch : 0} unit(s)</div>
      <div style="margin-bottom:6px">🩸 <strong>Units Requested:</strong> ${req.units} unit(s)</div>
      <div style="margin-bottom:6px">📅 <strong>Requested At:</strong> ${fmtDateTime(req.createdAt)}</div>
      ${req.notes ? `<div style="margin-top:8px;color:var(--text-m);font-style:italic">💬 ${esc(req.notes)}</div>` : ""}
    </div>
    ${
      isPending
        ? `<div style="display:flex;gap:12px;margin-top:16px">
           <button class="btn green flex-1" style="flex:1" onclick="respondBloodBankAlert('${req.id}','accepted');closeModal('modal-req')">✅ Accept Request</button>
           <button class="btn danger flex-1" style="flex:1" onclick="respondBloodBankAlert('${req.id}','rejected');closeModal('modal-req')">❌ Decline Request</button>
         </div>`
        : isConfirmed || isAccepted || isDeclined
          ? `<div style="text-align:center;padding:10px;background:var(--border-l);border-radius:10px;font-weight:700;color:var(--text-m)">Response recorded: ${isConfirmed ? `✅ Confirmed by Hospital (${bm.unitsSecured || ""} units)` : isAccepted ? `🟡 Accepted (${bm.reservedUnits !== undefined ? bm.reservedUnits : bm.unitsSecured || ""} units reserved · Awaiting confirmation)` : "❌ Declined"} (${fmtDateTime(bm.respondedAt)})</div>`
          : `<div style="text-align:center;padding:10px;background:var(--border-l);border-radius:10px;font-weight:700;color:var(--text-m)">✓ Request Completed · This request has already been fulfilled by the hospital.</div>`
    }`;
  openModal("modal-req");
}

function respondBloodBankAlert(reqId, status) {
  if (!CU || CU.role !== "bloodbank") return;
  const normalized = status === "declined" ? "rejected" : status;

  apiFetch(
    `${API_BASE}/api/bloodbanks/${encodeURIComponent(CU.id)}/alerts/${encodeURIComponent(reqId)}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: normalized }),
    },
  )
    .then(async (res) => {
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        toast(data.error || "Failed to submit response", "err");
        return;
      }
      const data = await res.json();
      if (normalized === "accepted") {
        toast(
          `✅ Request accepted! Secured ${data.unitsSecured || 0} unit(s) from inventory.`,
          "ok",
        );
      } else {
        toast("Request declined.", "info");
      }

      // Concurrently refresh authoritative backend inventory and alerts
      Promise.all([
        fetchBloodBankInventory(CU.id),
        fetchBloodBankAlerts(CU.id),
      ]).then(() => {
        renderBDash();
        renderBAlerts();
        updateBadge();
      });
    })
    .catch(() => {
      toast(
        "Failed to submit response. Please check server connection.",
        "err",
      );
    });
}

/* ── SHARED ── */
function renderCompat(pfx) {
  const t = $("compat-" + pfx);
  if (!t || t.innerHTML.trim()) return;
  const G = ["A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"];
  const C = {
    "O-": G,
    "O+": ["A+", "B+", "O+", "AB+"],
    "A-": ["A+", "A-", "AB+", "AB-"],
    "A+": ["A+", "AB+"],
    "B-": ["B+", "B-", "AB+", "AB-"],
    "B+": ["B+", "AB+"],
    "AB-": ["AB+", "AB-"],
    "AB+": ["AB+"],
  };
  let h = `<table class="compat-table"><thead><tr><th>Donor↓/Recipient→</th>${G.map((g) => `<th>${g}</th>`).join("")}</tr></thead><tbody>`;
  G.forEach((donor) => {
    h += `<tr><td style="font-weight:800;font-family:var(--display)">${donor}</td>${G.map((rec) => `<td class="${(C[donor] || []).includes(rec) ? "ct-yes" : "ct-no"}">${(C[donor] || []).includes(rec) ? "✅" : ""}</td>`).join("")}</tr>`;
  });
  t.innerHTML = h + "</tbody></table>";
}
const FAQS = [
  {
    q: "How often can I donate blood?",
    a: "Whole blood: every 90 days. Platelets: every 7 days. Plasma: every 28 days.",
  },
  {
    q: "What are the eligibility criteria?",
    a: "You must be 18–65 years old, weigh at least 45kg, and be in good health.",
  },
  {
    q: "Does donating hurt?",
    a: "A slight pinch when the needle is inserted. Most donors feel minimal discomfort.",
  },
  {
    q: "How long does donation take?",
    a: "The draw takes 8–10 minutes. Total with registration: 45–60 minutes.",
  },
  {
    q: "Can I eat before donating?",
    a: "Yes — eat iron-rich food and drink plenty of water. Avoid fatty foods.",
  },
  {
    q: "Is donating safe?",
    a: "Yes. New sterile needles are used and discarded for every donor.",
  },
  {
    q: "Why update my location?",
    a: "BloodLink uses GPS to match you with nearby hospitals. Without it, you miss alerts.",
  },
  {
    q: "Can hospitals see my personal info?",
    a: "Only name, phone, blood group, city, and report — visible only when searching for donors.",
  },
  {
    q: "How does Smart Radius work?",
    a: "You choose a radius (e.g. 5km). Top 3 closest donors are directly contacted, next 7 on standby.",
  },
  {
    q: "How do I get notified?",
    a: 'Via the Alerts section. Set status to "Available" to receive requests.',
  },
];
function renderFAQ(pfx) {
  const t = $("faq-" + pfx);
  if (!t || t.children.length) return;
  t.innerHTML = FAQS.map(
    (f) =>
      `<div class="faq-item" onclick="this.classList.toggle('open')"><div class="faq-q">${f.q}<span class="faq-arr">▾</span></div><div class="faq-a">${f.a}</div></div>`,
  ).join("");
}
function empty(icon, title, msg) {
  return `<div class="empty"><div class="empty-icon">${icon}</div><h3>${title}</h3><p>${msg}</p></div>`;
}

/* ── BOOT ── */
(async function () {
  cleanupLegacyStorage();
  const s = DB.obj("session");
  if (s && s.id && s.token) {
    const u = await getUser(s.id);
    if (u && u.id) {
      CU = { ...u, token: s.token };
      $("scr-landing").style.display = "none";
      $("scr-app").style.display = "block";
      initApp();
      return;
    }
  } else if (s && s.id && !s.token) {
    DB.del("session");
  }
  initLanding();
})();

document.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && $("scr-auth")?.style.display === "flex") {
    if ($("auth-login-form")?.style.display !== "none") doLogin();
  }
});
