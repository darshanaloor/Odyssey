/**
 * Odyssey - Disruption Mitigation Engine Frontend Application
 * Vanilla JavaScript (No external frameworks, no build step)
 */

(function () {
  'use strict';

  console.log("Odyssey app.js loaded");

  // Application State
  const state = {
    currentTripId: 'TRIP-DEMO-001',
    trip: null,
    tripsList: [],
    disruptionResult: null,
    recoveryData: null,
    selectedOptionId: null,
    planData: null,
    checklistState: {}, // id -> boolean
    parsedBookingsForCreate: [],
    currentRelaxation: {},
    statusMode: 'SIMULATED',
    statusPollingTimer: null,
    isStatusRequestInFlight: false,
    currentUser: null,
    loginReturnTarget: null,
  };

  // DOM Elements Cache
  const elements = {
    // Header & Navigation
    tripStatusBadge: document.getElementById('trip-status-badge'),
    btnOpenCreateTrip: document.getElementById('btn-open-create-trip'),
    btnLoadDemo: document.getElementById('btn-load-demo'),
    btnReset: document.getElementById('btn-reset'),
    btnOpenLogin: document.getElementById('btn-open-login'),
    btnOpenRegister: document.getElementById('btn-open-register'),
    userProfilePill: document.getElementById('user-profile-pill'),
    userDisplayName: document.getElementById('user-display-name'),
    navBtnDirectory: document.getElementById('nav-btn-directory'),
    navBtnWorkspace: document.getElementById('nav-btn-workspace'),
    navTripsCount: document.getElementById('nav-trips-count'),
    navCurrentTripTag: document.getElementById('nav-current-trip-tag'),

    // Views
    sectionTripsDirectory: document.getElementById('section-trips-directory'),
    sectionWorkspace: document.getElementById('section-workspace'),

    // Directory View Elements
    btnRefreshTrips: document.getElementById('btn-refresh-trips'),
    btnDirNewTrip: document.getElementById('btn-dir-new-trip'),
    directoryTripsCount: document.getElementById('directory-trips-count'),
    tripsDirectoryList: document.getElementById('trips-directory-list'),
    panelAuthRequired: document.getElementById('panel-auth-required'),
    btnPanelLogin: document.getElementById('btn-panel-login'),
    btnPanelRegister: document.getElementById('btn-panel-register'),

    // Workspace Navigation & Overview
    btnBackToDirectory: document.getElementById('btn-back-to-directory'),
    workspaceTripIndicator: document.getElementById('workspace-trip-indicator'),
    tripIdDisplay: document.getElementById('trip-id-display'),
    tripOverviewStatusBadge: document.getElementById('trip-overview-status-badge'),
    tripTitle: document.getElementById('trip-title'),
    tripTraveller: document.getElementById('trip-traveller'),
    tripBudget: document.getElementById('trip-budget'),
    tripDeadline: document.getElementById('trip-deadline'),
    tripOvernight: document.getElementById('trip-overnight'),
    tripModes: document.getElementById('trip-modes'),
    tripCabStatus: document.getElementById('trip-cab-status'),
    tripHotelStatus: document.getElementById('trip-hotel-status'),
    btnEditTrip: document.getElementById('btn-edit-trip'),

    // Auto Disruption Banner
    autoDisruptionBanner: document.getElementById('auto-disruption-banner'),
    autoDisruptionTitle: document.getElementById('auto-disruption-title'),
    autoDisruptionMessage: document.getElementById('auto-disruption-message'),
    btnGotoRecovery: document.getElementById('btn-goto-recovery'),

    // Live Status & Monitoring
    sectionLiveStatus: document.getElementById('section-live-status'),
    statusModeBadge: document.getElementById('status-mode-badge'),
    lastCheckedLabel: document.getElementById('last-checked-label'),
    btnCheckStatusNow: document.getElementById('btn-check-status-now'),
    statusWindowNote: document.getElementById('status-window-note'),
    simulateControlContainer: document.getElementById('simulate-control-container'),
    selectSimulateBooking: document.getElementById('select-simulate-booking'),
    selectSimulateState: document.getElementById('select-simulate-state'),
    groupSimulateDelay: document.getElementById('group-simulate-delay'),
    inputSimulateDelay: document.getElementById('input-simulate-delay'),
    btnApplySimulation: document.getElementById('btn-apply-simulation'),

    // Timeline
    bookingCountBadge: document.getElementById('booking-count'),
    timelineContainer: document.getElementById('timeline-container'),
    btnAddBooking: document.getElementById('btn-add-booking'),

    // Disruption Panel
    selectDisruptionBooking: document.getElementById('select-disruption-booking'),
    selectDisruptionType: document.getElementById('select-disruption-type'),
    groupDelayMinutes: document.getElementById('group-delay-minutes'),
    inputDelayMinutes: document.getElementById('input-delay-minutes'),
    inputDisruptionReason: document.getElementById('input-disruption-reason'),
    btnInjectDisruption: document.getElementById('btn-inject-disruption'),
    disruptionOutputContainer: document.getElementById('disruption-output-container'),
    smsRecipient: document.getElementById('sms-recipient'),
    smsMessageBox: document.getElementById('sms-message-box'),
    impactSummaryBox: document.getElementById('impact-summary-box'),
    cascadeList: document.getElementById('cascade-list'),

    // Recovery Options
    btnFindRecovery: document.getElementById('btn-find-recovery'),
    noPerfectBanner: document.getElementById('no-perfect-banner'),
    noPerfectText: document.getElementById('no-perfect-text'),
    relaxConstraintsBar: document.getElementById('relax-constraints-bar'),
    relaxationStatusBadge: document.getElementById('relaxation-status-badge'),
    btnRelaxOvernight: document.getElementById('btn-relax-overnight'),
    btnRelaxDeadline: document.getElementById('btn-relax-deadline'),
    btnRelaxBudget: document.getElementById('btn-relax-budget'),
    btnRelaxReset: document.getElementById('btn-relax-reset'),
    optionsCardsContainer: document.getElementById('options-cards-container'),
    rejectedOptionsWrapper: document.getElementById('rejected-options-wrapper'),
    btnToggleRejected: document.getElementById('btn-toggle-rejected'),
    rejectedCount: document.getElementById('rejected-count'),
    rejectedOptionsContent: document.getElementById('rejected-options-content'),
    rejectedOptionsList: document.getElementById('rejected-options-list'),

    // Recovery Plan
    sectionPlan: document.getElementById('section-plan'),
    selectedOptionBanner: document.getElementById('selected-option-banner'),
    tradeoffBox: document.getElementById('tradeoff-box'),
    diffTableBody: document.getElementById('diff-table-body'),
    costLinesContainer: document.getElementById('cost-lines-container'),
    costNetTotal: document.getElementById('cost-net-total'),
    costMaxBudget: document.getElementById('cost-max-budget'),
    costRemainingBudget: document.getElementById('cost-remaining-budget'),
    checklistContainer: document.getElementById('checklist-container'),
    planHotelMessageSection: document.getElementById('plan-hotel-message-section'),
    hotelMessageBox: document.getElementById('hotel-message-box'),
    btnCopyHotelMessage: document.getElementById('btn-copy-hotel-message'),
    btnRejectPlan: document.getElementById('btn-reject-plan'),
    btnApprovePlan: document.getElementById('btn-approve-plan'),

    // Resolved Banner
    sectionResolvedBanner: document.getElementById('section-resolved-banner'),

    // Audit Log
    auditCountBadge: document.getElementById('audit-count'),
    auditTableBody: document.getElementById('audit-table-body'),

    // Toast Container
    toastContainer: document.getElementById('toast-container'),

    // Phase 8: Notifications & Dispatch
    sectionNotifications: document.getElementById('section-notifications'),
    btnRefreshNotifications: document.getElementById('btn-refresh-notifications'),
    btnSendTestAlert: document.getElementById('btn-send-test-alert'),
    badgeChannelEmail: document.getElementById('badge-channel-email'),
    badgeChannelSms: document.getElementById('badge-channel-sms'),
    badgeChannelWhatsapp: document.getElementById('badge-channel-whatsapp'),
    notificationsTableBody: document.getElementById('notifications-table-body'),
    btnSendHotelEmail: document.getElementById('btn-send-hotel-email'),
    linkMailtoHotel: document.getElementById('link-mailto-hotel'),
    hotelMessageRecipientInfo: document.getElementById('hotel-message-recipient-info'),

    // Modals: Create Trip
    modalCreateTrip: document.getElementById('modal-create-trip'),
    btnCloseCreateModal: document.getElementById('btn-close-create-modal'),
    btnCancelCreate: document.getElementById('btn-cancel-create'),
    formCreateTrip: document.getElementById('form-create-trip'),
    createTripTitle: document.getElementById('create-trip-title'),
    createTripTraveller: document.getElementById('create-trip-traveller'),
    createTripPhone: document.getElementById('create-trip-phone'),
    createTripEmail: document.getElementById('create-trip-email'),
    createTripBudget: document.getElementById('create-trip-budget'),
    createTripDeadline: document.getElementById('create-trip-deadline'),
    createTripDesc: document.getElementById('create-trip-desc'),
    createTripNotifyEmail: document.getElementById('create-trip-notify-email'),
    createTripNotifySms: document.getElementById('create-trip-notify-sms'),
    createTripNotifyWhatsapp: document.getElementById('create-trip-notify-whatsapp'),
    createTripHotelEmail: document.getElementById('create-trip-hotel-email'),
    btnAddCommitment: document.getElementById('btn-add-commitment'),
    createCommitmentsList: document.getElementById('create-commitments-list'),
    createCommitmentsEmpty: document.getElementById('create-commitments-empty'),
    createTripAvoidOvernight: document.getElementById('create-trip-avoid-overnight'),
    createTripNeedsCab: document.getElementById('create-trip-needs-cab'),
    createCabFields: document.getElementById('create-cab-fields'),
    createTripCabDropoff: document.getElementById('create-trip-cab-dropoff'),
    createTripCabProvider: document.getElementById('create-trip-cab-provider'),
    errCreateCabDropoff: document.getElementById('err-create-cab-dropoff'),
    createTripNeedsHotel: document.getElementById('create-trip-needs-hotel'),
    createHotelFields: document.getElementById('create-hotel-fields'),
    createTripHotelName: document.getElementById('create-trip-hotel-name'),
    createTripHotelAddress: document.getElementById('create-trip-hotel-address'),
    createTripHotelCheckin: document.getElementById('create-trip-hotel-checkin'),
    createTripHotelCheckout: document.getElementById('create-trip-hotel-checkout'),
    createTripHotelCost: document.getElementById('create-trip-hotel-cost'),
    errCreateHotelName: document.getElementById('err-create-hotel-name'),
    errCreateHotelAddress: document.getElementById('err-create-hotel-address'),
    createEmailText: document.getElementById('create-email-text'),
    btnParseEmailCreate: document.getElementById('btn-parse-email-create'),
    createParserUnparsed: document.getElementById('create-parser-unparsed'),
    btnAddBookingRow: document.getElementById('btn-add-booking-row'),
    createBuilderCount: document.getElementById('create-builder-count'),
    createBuilderEmpty: document.getElementById('create-builder-empty'),
    createBookingsBuilderList: document.getElementById('create-bookings-builder-list'),
    errCreateTitle: document.getElementById('err-create-title'),
    errCreatePhone: document.getElementById('err-create-phone'),
    errCreateEmail: document.getElementById('err-create-email'),
    errCreateBudget: document.getElementById('err-create-budget'),
    errCreateDeadline: document.getElementById('err-create-deadline'),
    errCreateBookings: document.getElementById('err-create-bookings'),

    // Modals: Edit Trip
    modalEditTrip: document.getElementById('modal-edit-trip'),
    formEditTrip: document.getElementById('form-edit-trip'),
    btnCloseEditModal: document.getElementById('btn-close-edit-modal'),
    btnCancelEditTrip: document.getElementById('btn-cancel-edit-trip'),
    btnSaveEditTrip: document.getElementById('btn-save-edit-trip'),
    editTripTitle: document.getElementById('edit-trip-title'),
    editTripTraveller: document.getElementById('edit-trip-traveller'),
    editTripPhone: document.getElementById('edit-trip-phone'),
    editTripEmail: document.getElementById('edit-trip-email'),
    editTripBudget: document.getElementById('edit-trip-budget'),
    editTripDeadline: document.getElementById('edit-trip-deadline'),
    editTripDesc: document.getElementById('edit-trip-desc'),
    editTripNotifyEmail: document.getElementById('edit-trip-notify-email'),
    editTripNotifySms: document.getElementById('edit-trip-notify-sms'),
    editTripNotifyWhatsapp: document.getElementById('edit-trip-notify-whatsapp'),
    editTripAvoidOvernight: document.getElementById('edit-trip-avoid-overnight'),
    editTripNeedsCab: document.getElementById('edit-trip-needs-cab'),
    editCabFields: document.getElementById('edit-cab-fields'),
    editTripCabDropoff: document.getElementById('edit-trip-cab-dropoff'),
    editTripCabProvider: document.getElementById('edit-trip-cab-provider'),
    errEditTitle: document.getElementById('err-edit-title'),
    errEditPhone: document.getElementById('err-edit-phone'),
    errEditEmail: document.getElementById('err-edit-email'),
    errEditBudget: document.getElementById('err-edit-budget'),
    errEditDeadline: document.getElementById('err-edit-deadline'),
    errEditCabDropoff: document.getElementById('err-edit-cab-dropoff'),

    // Modals: Booking Editor
    modalBookingEditor: document.getElementById('modal-booking-editor'),
    btnCloseBookingModal: document.getElementById('btn-close-booking-modal'),
    btnCancelBooking: document.getElementById('btn-cancel-booking'),
    formBookingEditor: document.getElementById('form-booking-editor'),
    modalBookingTitle: document.getElementById('modal-booking-title'),
    editBookingId: document.getElementById('edit-booking-id'),
    bookingInputType: document.getElementById('booking-input-type'),
    bookingInputProvider: document.getElementById('booking-input-provider'),
    bookingInputFrom: document.getElementById('booking-input-from'),
    bookingInputTo: document.getElementById('booking-input-to'),
    bookingInputStart: document.getElementById('booking-input-start'),
    bookingInputEnd: document.getElementById('booking-input-end'),
    bookingInputCost: document.getElementById('booking-input-cost'),
    bookingInputStatus: document.getElementById('booking-input-status'),
    bookingDependenciesPicker: document.getElementById('booking-dependencies-picker'),
    errBookingProvider: document.getElementById('err-booking-provider'),
    errBookingStart: document.getElementById('err-booking-start'),
    errBookingEnd: document.getElementById('err-booking-end'),
    errBookingCost: document.getElementById('err-booking-cost'),
    errBookingCycle: document.getElementById('err-booking-cycle'),
    bookingInputHotelEmail: document.getElementById('booking-input-hotel-email'),

    // Status disclaimer tag
    statusDisclaimerTag: document.getElementById('status-disclaimer-tag'),

    // Modals: Report Problem
    modalReportProblem: document.getElementById('modal-report-problem'),
    btnCloseReportModal: document.getElementById('btn-close-report-modal'),
    btnCancelReportProblem: document.getElementById('btn-cancel-report-problem'),
    formReportProblem: document.getElementById('form-report-problem'),
    reportBookingId: document.getElementById('report-booking-id'),
    reportBookingSummary: document.getElementById('report-booking-summary'),
    reportProblemType: document.getElementById('report-problem-type'),
    groupReportDelay: document.getElementById('group-report-delay'),
    reportDelayMinutes: document.getElementById('report-delay-minutes'),
    reportProblemNote: document.getElementById('report-problem-note'),
    btnSubmitReportProblem: document.getElementById('btn-submit-report-problem'),

    // Modals: Auth (Phase 9)
    modalLogin: document.getElementById('modal-login'),
    btnCloseLoginModal: document.getElementById('btn-close-login-modal'),
    formLogin: document.getElementById('form-login'),
    loginEmail: document.getElementById('login-email'),
    loginPassword: document.getElementById('login-password'),
    errLogin: document.getElementById('err-login'),
    btnSubmitLogin: document.getElementById('btn-submit-login'),
    linkGotoRegister: document.getElementById('link-goto-register'),

    modalRegister: document.getElementById('modal-register'),
    btnCloseRegisterModal: document.getElementById('btn-close-register-modal'),
    formRegister: document.getElementById('form-register'),
    regName: document.getElementById('reg-name'),
    regEmail: document.getElementById('reg-email'),
    regPassword: document.getElementById('reg-password'),
    regPhone: document.getElementById('reg-phone'),
    errRegister: document.getElementById('err-register'),
    btnSubmitRegister: document.getElementById('btn-submit-register'),
    linkGotoLogin: document.getElementById('link-goto-login'),

    modalAccount: document.getElementById('modal-account'),
    btnCloseAccountModal: document.getElementById('btn-close-account-modal'),
    errAccount: document.getElementById('err-account'),
    formAccountProfile: document.getElementById('form-account-profile'),
    accountEmail: document.getElementById('account-email'),
    accountName: document.getElementById('account-name'),
    accountPhone: document.getElementById('account-phone'),
    btnSaveProfile: document.getElementById('btn-save-profile'),
    formAccountPassword: document.getElementById('form-account-password'),
    accountCurrPassword: document.getElementById('account-curr-password'),
    accountNewPassword: document.getElementById('account-new-password'),
    btnSubmitChangePassword: document.getElementById('btn-submit-change-password'),
    btnLogout: document.getElementById('btn-logout'),

    // Handoff & Booking Confirmation (Phase 9)
    sectionHandoffCard: document.getElementById('section-handoff-card'),
    handoffStatusBadge: document.getElementById('handoff-status-badge'),
    handoffCompletedBanner: document.getElementById('handoff-completed-banner'),
    handoffLegsList: document.getElementById('handoff-legs-list'),

    modalConfirmBooking: document.getElementById('modal-confirm-booking'),
    btnCloseConfirmModal: document.getElementById('btn-close-confirm-modal'),
    formConfirmBooking: document.getElementById('form-confirm-booking'),
    confirmBookingId: document.getElementById('confirm-booking-id'),
    confirmBookingSummary: document.getElementById('confirm-booking-summary'),
    confirmBookingRef: document.getElementById('confirm-booking-ref'),
    btnSubmitConfirmBooking: document.getElementById('btn-submit-confirm-booking'),
    btnCancelConfirmBooking: document.getElementById('btn-cancel-confirm-booking'),
  };

  // ==========================================
  // Utility Functions & Security Interceptors
  // ==========================================

  function getCsrfToken() {
    const match = document.cookie.match(new RegExp('(^|;\\s*)XSRF-TOKEN=([^;]*)'));
    return match ? decodeURIComponent(match[2]) : null;
  }

  async function fetchCsrfToken() {
    try {
      await originalFetch('/api/auth/csrf', { credentials: 'same-origin' });
    } catch (e) {
      // ignore
    }
  }

  function handleUnauthorized(url) {
    state.currentUser = null;
    updateAuthHeader();
    state.loginReturnTarget = {
      view: elements.sectionTripsDirectory && !elements.sectionTripsDirectory.classList.contains('hidden') ? 'directory' : 'workspace',
      tripId: state.currentTripId
    };
    openLoginModal();
    showToast('Authentication required. Please sign in.', 'warning');
  }

  const originalFetch = window.fetch;
  window.fetch = async function (url, options = {}) {
    options = options || {};
    options.headers = options.headers || {};
    const method = (options.method || 'GET').toUpperCase();
    if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
      const csrfToken = getCsrfToken();
      if (csrfToken && !(options.headers instanceof Headers) && !options.headers['X-XSRF-TOKEN'] && !options.headers['x-xsrf-token']) {
        options.headers['X-XSRF-TOKEN'] = csrfToken;
      }
    }
    if (!options.credentials) {
      options.credentials = 'same-origin';
    }

    const response = await originalFetch(url, options);

    if (response.status === 401 && typeof url === 'string' && !url.includes('/api/auth/me') && !url.includes('/api/auth/login')) {
      handleUnauthorized(url);
    }

    return response;
  };

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function toTitleCase(str) {
    if (!str) return '';
    return String(str).replace(/\b\w+/g, (txt) => {
      if (/^[A-Z]{2,5}$/.test(txt)) return txt;
      return txt.charAt(0).toUpperCase() + txt.substring(1).toLowerCase();
    });
  }

  function formatDateTime(isoStr) {
    if (!isoStr) return '-';
    const match = String(isoStr).match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
    if (match) {
      const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      const day = parseInt(match[3], 10);
      const month = months[parseInt(match[2], 10) - 1];
      const hour = match[4];
      const min = match[5];
      return `${day} ${month}, ${hour}:${min}`;
    }
    const d = new Date(isoStr);
    if (isNaN(d.getTime())) return String(isoStr);
    const day = d.getDate();
    const month = d.toLocaleString('en-US', { month: 'short' });
    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    return `${day} ${month}, ${hours}:${minutes}`;
  }

  function formatCurrency(amount) {
    if (amount === null || amount === undefined) return 'Rs 0.00';
    const num = Number(amount);
    return 'Rs ' + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function showToast(message, type = 'info') {
    if (!elements.toastContainer) return;
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.setAttribute('role', 'alert');
    toast.innerHTML = `
      <span>${escapeHtml(message)}</span>
      <button type="button" class="btn btn-sm btn-outline" style="padding: 2px 6px; font-size: 0.75rem;" aria-label="Close alert">&times;</button>
    `;
    const closeBtn = toast.querySelector('button');
    closeBtn.addEventListener('click', () => toast.remove());
    elements.toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(100%)';
      setTimeout(() => toast.remove(), 300);
    }, 5000);
  }

  function setButtonLoading(button, isLoading) {
    if (!button) return;
    if (isLoading) {
      button.classList.add('loading');
      button.disabled = true;
    } else {
      button.classList.remove('loading');
      button.disabled = false;
    }
  }

  function formatStatus(status) {
    if (!status) return '';
    return String(status).replace(/_/g, ' ');
  }

  function getStatusBadgeClass(status) {
    const s = String(status || '').toUpperCase().replace(/\s+/g, '_');
    switch (s) {
      case 'CONFIRMED':
      case 'SAFE':
      case 'RESOLVED':
        return 'badge-green';
      case 'AT_RISK':
      case 'DELAYED':
        return 'badge-amber';
      case 'CANCELLED':
      case 'DISRUPTED':
        return 'badge-red';
      case 'PROPOSED':
        return 'badge-purple';
      case 'REBOOKED':
        return 'badge-blue';
      default:
        return 'badge-neutral';
    }
  }

  // ==========================================
  // Navigation & View Switching
  // ==========================================

  function switchView(viewName) {
    if (viewName === 'directory') {
      stopStatusPolling();
      elements.sectionTripsDirectory.classList.remove('hidden');
      elements.sectionWorkspace.classList.add('hidden');
      elements.navBtnDirectory.classList.add('active');
      elements.navBtnWorkspace.classList.remove('active');
      fetchTripsDirectory();
    } else {
      elements.sectionTripsDirectory.classList.add('hidden');
      elements.sectionWorkspace.classList.remove('hidden');
      elements.navBtnDirectory.classList.remove('active');
      elements.navBtnWorkspace.classList.add('active');
      if (state.currentTripId) {
        elements.navCurrentTripTag.textContent = state.currentTripId;
        elements.workspaceTripIndicator.innerHTML = `Viewing: <strong>${escapeHtml(state.trip ? state.trip.title : state.currentTripId)}</strong>`;
        startStatusPolling(state.currentTripId);
      }
    }
  }

  // ==========================================
  // Trips Directory API & Rendering
  // ==========================================

  async function fetchTripsDirectory() {
    try {
      const res = await fetch('/api/trips');
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to fetch trips');
      }
      state.tripsList = data.data || [];
      renderTripsDirectory(state.tripsList);
    } catch (err) {
      showToast(err.message, 'danger');
      elements.tripsDirectoryList.innerHTML = `<p class="empty-state">Error loading trips: ${escapeHtml(err.message)}</p>`;
    }
  }

  function renderTripsDirectory(trips) {
    const count = trips.length;
    const countStr = count === 1 ? '1 trip' : `${count} trips`;
    elements.directoryTripsCount.textContent = countStr;
    elements.navTripsCount.textContent = count;

    if (count === 0) {
      elements.tripsDirectoryList.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1;">
          <p>No travel expeditions found in database.</p>
          <button id="btn-empty-demo" class="btn btn-primary" style="margin-top: 12px;">Load Demo Trip</button>
        </div>
      `;
      const btnEmptyDemo = document.getElementById('btn-empty-demo');
      if (btnEmptyDemo) {
        btnEmptyDemo.addEventListener('click', handleLoadDemo);
      }
      return;
    }

    elements.tripsDirectoryList.innerHTML = '';
    trips.forEach((trip) => {
      const card = document.createElement('div');
      card.className = 'trip-dir-card';

      const bookingCount = trip.bookings ? trip.bookings.length : 0;
      const bookingStr = bookingCount === 1 ? '1 booking' : `${bookingCount} bookings`;
      const maxBudget = trip.constraints && trip.constraints.maxBudget ? formatCurrency(trip.constraints.maxBudget) : 'No limit';
      const statusClass = getStatusBadgeClass(trip.status);

      card.innerHTML = `
        <div class="trip-dir-header">
          <div>
            <h3 class="trip-dir-title">${escapeHtml(trip.title)}</h3>
            <span class="trip-dir-id">${escapeHtml(trip.id)}</span>
          </div>
          <span class="badge ${statusClass}">${escapeHtml(formatStatus(trip.status))}</span>
        </div>
        <div class="trip-dir-body">
          <div class="trip-dir-meta-row">
            <span>Traveller:</span>
            <strong>${escapeHtml(trip.travellerName || 'Traveller')} ${trip.travellerPhone ? '(' + escapeHtml(trip.travellerPhone) + ')' : ''} ${trip.travellerEmail ? '&bull; ' + escapeHtml(trip.travellerEmail) : ''}</strong>
          </div>
          <div class="trip-dir-meta-row">
            <span>Itinerary:</span>
            <strong>${bookingStr}</strong>
          </div>
          <div class="trip-dir-meta-row">
            <span>Max Budget:</span>
            <strong>${maxBudget}</strong>
          </div>
          <div class="trip-dir-meta-row">
            <span>Created:</span>
            <span>${formatDateTime(trip.createdAt)}</span>
          </div>
        </div>
        <div class="trip-dir-footer">
          ${trip.id !== 'TRIP-DEMO-001' ? `
            <button class="btn btn-outline btn-sm btn-delete-trip" data-trip-id="${escapeHtml(trip.id)}" aria-label="Delete trip">
              Delete
            </button>
          ` : ''}
          <button class="btn btn-gold btn-sm btn-open-trip" data-trip-id="${escapeHtml(trip.id)}" aria-label="Open trip workspace">
            Open Workspace &rarr;
          </button>
        </div>
      `;

      elements.tripsDirectoryList.appendChild(card);
    });

    // Attach click listeners to cards
    elements.tripsDirectoryList.querySelectorAll('.btn-open-trip').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const tripId = e.currentTarget.getAttribute('data-trip-id');
        loadTripById(tripId);
      });
    });

    elements.tripsDirectoryList.querySelectorAll('.btn-delete-trip').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const tripId = e.currentTarget.getAttribute('data-trip-id');
        handleDeleteTrip(tripId);
      });
    });
  }

  async function handleDeleteTrip(tripId) {
    if (!confirm(`Are you sure you want to delete expedition ${tripId}? This will remove all bookings, plans, and audit history.`)) {
      return;
    }
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}`, { method: 'DELETE' });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to delete trip');
      }
      showToast(`Trip ${tripId} deleted successfully`, 'success');
      fetchTripsDirectory();
      if (state.currentTripId === tripId) {
        state.trip = null;
        state.currentTripId = null;
      }
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  // ==========================================
  // Trip Loading & Workspace Rendering
  // ==========================================

  async function loadTripById(tripId) {
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}`);
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || `Failed to load trip ${tripId}`);
      }

      state.currentTripId = tripId;
      state.trip = data.data;
      state.disruptionResult = null;
      state.recoveryData = null;
      state.selectedOptionId = null;
      state.planData = null;
      state.checklistState = {};

      renderWorkspace(state.trip);
      loadChannelStatus();
      loadNotificationHistory(tripId);
      switchView('workspace');
      showToast(`Loaded expedition: ${state.trip.title}`, 'info');
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  function renderWorkspace(trip) {
    if (!trip) return;

    // Header Badge & Indicators
    const statusText = formatStatus(trip.status);
    const badgeClass = getStatusBadgeClass(trip.status);

    elements.tripStatusBadge.textContent = statusText;
    elements.tripStatusBadge.className = `badge ${badgeClass}`;

    elements.tripOverviewStatusBadge.textContent = statusText;
    elements.tripOverviewStatusBadge.className = `badge ${badgeClass}`;

    elements.navCurrentTripTag.textContent = trip.id;
    elements.workspaceTripIndicator.innerHTML = `Viewing: <strong>${escapeHtml(trip.title)}</strong> (${escapeHtml(trip.id)})`;

    // Overview Card
    elements.tripIdDisplay.textContent = trip.id;
    elements.tripTitle.textContent = trip.title;
    elements.tripTraveller.textContent = `${trip.travellerName || 'Traveller'} ${trip.travellerPhone ? '(' + trip.travellerPhone + ')' : ''} ${trip.travellerEmail ? '• ' + trip.travellerEmail : ''}`;

    const constraints = trip.getConstraints ? trip.getConstraints : trip.constraints;
    if (constraints) {
      elements.tripBudget.textContent = constraints.maxBudget ? formatCurrency(constraints.maxBudget) : 'No limit';
      elements.tripDeadline.textContent = constraints.mustArriveBy ? formatDateTime(constraints.mustArriveBy) : 'None';
      elements.tripOvernight.textContent = constraints.avoidOvernightTravel ? 'Avoided (Daytime Only)' : 'Permitted';
      elements.tripModes.textContent = constraints.preferredModes && constraints.preferredModes.length > 0
        ? constraints.preferredModes.join(', ')
        : 'All Modes';
    } else {
      elements.tripBudget.textContent = 'None';
      elements.tripDeadline.textContent = 'None';
      elements.tripOvernight.textContent = 'Permitted';
      elements.tripModes.textContent = 'All Modes';
    }

    // Cab & Hotel Preferences
    if (elements.tripCabStatus) {
      elements.tripCabStatus.textContent = trip.needsDestinationCab
        ? `Requested (${trip.destinationCabDropoff || 'Destination'})`
        : 'Not requested';
    }
    if (elements.tripHotelStatus) {
      const hotelBkg = (trip.bookings || []).find((b) => b.type === 'HOTEL');
      elements.tripHotelStatus.textContent = hotelBkg
        ? `${hotelBkg.provider} (${toTitleCase(hotelBkg.from)})`
        : 'Not booked';
    }

    // Auto-disruption banner check
    if (elements.autoDisruptionBanner) {
      const autoDisruptions = (trip.activeDisruptions || []).filter(
        (d) => d.reason && d.reason.includes('Detected automatically')
      );
      if (autoDisruptions.length > 0 && trip.status === 'DISRUPTED') {
        const d = autoDisruptions[autoDisruptions.length - 1];
        const bkg = (trip.bookings || []).find((b) => b.id === d.bookingId);
        const bkgDesc = bkg ? `[${bkg.type}] ${bkg.provider}` : (d.bookingId || 'Booking');
        const detail = d.type === 'CANCELLATION' ? 'cancelled' : `delayed by ${d.delayMinutes || 0} min`;
        elements.autoDisruptionMessage.textContent = `Disruption detected automatically: ${bkgDesc} ${detail}`;
        elements.autoDisruptionBanner.classList.remove('hidden');
      } else {
        elements.autoDisruptionBanner.classList.add('hidden');
      }
    }

    // Status Monitoring - Last Checked Timestamp
    if (elements.lastCheckedLabel) {
      let latestChecked = null;
      (trip.bookings || []).forEach((b) => {
        if (b.lastCheckedAt) {
          const d = new Date(b.lastCheckedAt);
          if (!latestChecked || d > latestChecked) {
            latestChecked = d;
          }
        }
      });
      elements.lastCheckedLabel.textContent = latestChecked
        ? `Last checked at: ${formatDateTime(latestChecked)}`
        : 'Last checked at: Never';
    }

    // Populate Simulate Booking Dropdown
    if (elements.selectSimulateBooking) {
      const prevVal = elements.selectSimulateBooking.value;
      elements.selectSimulateBooking.innerHTML = '<option value="">-- Select a booking to simulate --</option>';
      (trip.bookings || []).forEach((b) => {
        const opt = document.createElement('option');
        opt.value = b.id;
        opt.textContent = `[${b.type}] ${b.provider} (${b.id})`;
        if (b.id === prevVal) opt.selected = true;
        elements.selectSimulateBooking.appendChild(opt);
      });
    }

    // Status / Recovery Note Callout
    if (elements.statusWindowNote) {
      if (trip.recoveryNote) {
        elements.statusWindowNote.textContent = trip.recoveryNote;
        elements.statusWindowNote.classList.remove('hidden');
      } else {
        elements.statusWindowNote.classList.add('hidden');
      }
    }

    // Timeline Bookings
    renderTimeline(trip.bookings || []);

    // Disruption Select Dropdown
    populateDisruptionBookingDropdown(trip.bookings || []);

    // Disruption Button State
    const isDisrupted = trip.status === 'DISRUPTED';
    const isResolved = trip.status === 'RESOLVED';
    const isCompletedBookings = trip.status === 'COMPLETED_BOOKINGS';

    elements.btnFindRecovery.disabled = !isDisrupted;
    elements.btnFindRecovery.title = isDisrupted
      ? 'Find optimal recovery options'
      : 'Recovery options can only be searched when a trip is disrupted.';

    // Show/hide resolved banner
    if (isResolved || isCompletedBookings) {
      elements.sectionResolvedBanner.classList.remove('hidden');
    } else {
      elements.sectionResolvedBanner.classList.add('hidden');
    }

    // Phase 9: Render Booking Hand-off card
    renderHandoffCard(trip);

    // Render Options if already present on trip
    if (trip.options && trip.options.length > 0) {
      renderRecoveryOptions({ options: trip.options, rejectedOptions: [] });
    } else {
      const noteHtml = trip.recoveryNote
        ? `<p class="empty-state alert-stale-notice">${escapeHtml(trip.recoveryNote)}</p>`
        : '<p class="empty-state">No recovery options generated yet. Inject a disruption or report a problem and click "Find Recovery Options".</p>';
      elements.optionsCardsContainer.innerHTML = noteHtml;
      elements.noPerfectBanner.classList.add('hidden');
      elements.rejectedOptionsWrapper.classList.add('hidden');
    }

    // Render Latest Plan if present
    if (trip.latestPlan) {
      state.planData = trip.latestPlan;
      renderPlan(trip.latestPlan);
    } else {
      elements.sectionPlan.classList.add('hidden');
    }

    // Audit Log
    renderAuditLog(trip.auditLog || []);
  }

  // ==========================================
  // Timeline Rendering
  // ==========================================

  function renderTimeline(bookings) {
    const container = elements.timelineContainer;
    const count = bookings.length;
    elements.bookingCountBadge.textContent = count === 1 ? '1 booking' : `${count} bookings`;

    if (count === 0) {
      container.innerHTML = '<p class="empty-state">No bookings in this itinerary yet. Click "+ Add Booking" above to add transit, lodging, or events.</p>';
      return;
    }

    // Sort the timeline by startTime ascending (ties by id)
    const sortedBookings = [...bookings].sort((a, b) => {
      const tA = a.startTime ? new Date(a.startTime).getTime() : 0;
      const tB = b.startTime ? new Date(b.startTime).getTime() : 0;
      if (tA !== tB) return tA - tB;
      return String(a.id || '').localeCompare(String(b.id || ''));
    });

    // Provider map for depends-on line to show provider name, not raw id
    const providerMap = {};
    sortedBookings.forEach((b) => {
      if (b.id) providerMap[b.id] = b.provider || b.id;
    });

    container.innerHTML = '';
    sortedBookings.forEach((booking) => {
      const item = document.createElement('div');
      item.className = 'timeline-item';

      const statusClass = getStatusBadgeClass(booking.status);
      const isProposed = booking.status === 'PROPOSED';
      const isCancelled = booking.status === 'CANCELLED';
      const isRebooked = booking.status === 'REBOOKED';

      let markerClass = 'marker-default';
      if (booking.type === 'FLIGHT') markerClass = 'marker-flight';
      if (booking.type === 'CAB') markerClass = 'marker-cab';
      if (booking.type === 'HOTEL') markerClass = 'marker-hotel';
      if (booking.type === 'EVENT') markerClass = 'marker-event';
      if (booking.type === 'TRAIN') markerClass = 'marker-train';
      if (booking.type === 'BUS') markerClass = 'marker-bus';

      const dependsOnStr = booking.dependsOn && booking.dependsOn.length > 0
        ? `Depends on: ${booking.dependsOn.map((id) => providerMap[id] || id).join(', ')}`
        : 'Root leg (No dependencies)';

      const isEstimated = booking.source === 'ESTIMATED';

      // Phase 7: Per-booking status chip
      let chipHtml = '';
      const bState = booking.lastStatusState || 'ON_TIME';
      const bDelay = booking.lastDelayMinutes || 0;
      const bSource = booking.lastStatusSource || 'SIMULATED';
      // Label simulated data "Simulated" everywhere, never "Live"
      const isSimulated = !(bSource === 'LIVE' && state.statusMode === 'LIVE');
      const prefix = isSimulated ? 'Simulated: ' : 'Live: ';
      const disclaimer = isSimulated ? 'Simulated data, not a real tracking source' : 'Live carrier data';
      const checkedTimeStr = booking.lastCheckedAt ? formatDateTime(booking.lastCheckedAt) : 'Never checked';

      if (bState === 'ON_TIME') {
        chipHtml = `<span class="status-chip chip-on-time" title="Checked: ${checkedTimeStr} • ${disclaimer}"><span class="chip-dot"></span> ${prefix}On time</span>`;
      } else if (bState === 'DELAYED') {
        chipHtml = `<span class="status-chip chip-delayed" title="Checked: ${checkedTimeStr} • ${disclaimer}"><span class="chip-dot"></span> ${prefix}Delayed ${bDelay} min</span>`;
      } else if (bState === 'CANCELLED') {
        chipHtml = `<span class="status-chip chip-cancelled" title="Checked: ${checkedTimeStr} • ${disclaimer}"><span class="chip-dot"></span> ${prefix}Cancelled</span>`;
      } else {
        const errText = escapeHtml(booking.statusCheckError || 'Status unavailable');
        chipHtml = `<span class="status-chip chip-unavailable" title="${errText} • ${checkedTimeStr} • ${disclaimer}"><span class="chip-dot"></span> ${prefix}Status unavailable</span>`;
      }

      item.innerHTML = `
        <div class="timeline-marker ${markerClass}">
          <span class="marker-dot"></span>
          <span class="timeline-line"></span>
        </div>
        <div class="timeline-card ${isCancelled ? 'card-cancelled' : ''} ${isProposed ? 'card-proposed' : ''} ${isRebooked ? 'card-rebooked' : ''}">
          <div class="timeline-card-header">
            <div class="timeline-title-group">
              <span class="booking-type-tag">${escapeHtml(booking.type)}</span>
              <strong class="booking-provider">${escapeHtml(booking.provider)}</strong>
              ${chipHtml}
              ${isRebooked ? '<span class="badge badge-blue">REBOOKED</span>' : ''}
              ${isEstimated ? '<span class="badge badge-warning badge-estimated">ESTIMATED</span>' : ''}
              ${isProposed ? '<span class="badge badge-purple">Proposed</span>' : ''}
            </div>
            <div class="booking-header-actions">
              <span class="badge ${statusClass}">${escapeHtml(formatStatus(booking.status))}</span>
              <button type="button" class="btn-report-problem" data-booking-id="${escapeHtml(booking.id)}" title="Report a problem for this booking">Report Problem</button>
              <button class="btn-icon-sm btn-edit-booking" data-booking-id="${escapeHtml(booking.id)}" title="Edit booking">Edit</button>
              <button class="btn-icon-sm danger btn-delete-booking" data-booking-id="${escapeHtml(booking.id)}" title="Delete booking">&times;</button>
            </div>
          </div>
          <div class="timeline-card-body">
            <div class="timeline-route">
              <span class="route-from">${escapeHtml(toTitleCase(booking.from || '-'))}</span>
              <span class="route-arrow">&rarr;</span>
              <span class="route-to">${escapeHtml(toTitleCase(booking.to || '-'))}</span>
            </div>
            <div class="timeline-meta-grid">
              <div class="meta-item">
                <span class="meta-label">Schedule</span>
                <span class="meta-value">${formatDateTime(booking.startTime)} - ${formatDateTime(booking.endTime)}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">Fare</span>
                <span class="meta-value">${formatCurrency(booking.cost)}</span>
              </div>
              <div class="meta-item full-row">
                <span class="meta-label">Dependency</span>
                <span class="meta-value font-mono text-muted">${escapeHtml(dependsOnStr)}</span>
              </div>
            </div>
          </div>
        </div>
      `;

      container.appendChild(item);
    });

    // Attach edit / delete / report listeners
    container.querySelectorAll('.btn-report-problem').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const bkgId = e.currentTarget.getAttribute('data-booking-id');
        openReportProblemModal(bkgId);
      });
    });

    container.querySelectorAll('.btn-edit-booking').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const bkgId = e.currentTarget.getAttribute('data-booking-id');
        openBookingEditor(bkgId);
      });
    });

    container.querySelectorAll('.btn-delete-booking').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const bkgId = e.currentTarget.getAttribute('data-booking-id');
        handleDeleteBooking(bkgId);
      });
    });
  }

  function populateDisruptionBookingDropdown(bookings) {
    const select = elements.selectDisruptionBooking;
    select.innerHTML = '<option value="">-- Select a booking to disrupt --</option>';

    bookings.forEach((b) => {
      const opt = document.createElement('option');
      opt.value = b.id;
      // Short label format: "[TYPE] provider"
      opt.textContent = `[${b.type}] ${b.provider} (${b.id})`;
      select.appendChild(opt);
    });
  }

  // ==========================================
  // Phase 7: Live Status & Simulation Management
  // ==========================================

  async function fetchStatusMode() {
    try {
      const res = await fetch('/api/status/mode');
      const data = await res.json();
      if (res.ok && data.success && data.data) {
        state.statusMode = data.data.mode || 'SIMULATED';
      }
    } catch (e) {
      state.statusMode = 'SIMULATED';
    }
    updateStatusModeUI();
  }

  function updateStatusModeUI() {
    if (!elements.statusModeBadge) return;
    if (state.statusMode === 'LIVE') {
      elements.statusModeBadge.textContent = 'Live status';
      elements.statusModeBadge.className = 'badge badge-cyan';
      if (elements.statusDisclaimerTag) elements.statusDisclaimerTag.classList.add('hidden');
      if (elements.simulateControlContainer) elements.simulateControlContainer.classList.add('hidden');
    } else {
      elements.statusModeBadge.textContent = 'Simulated';
      elements.statusModeBadge.className = 'badge badge-purple';
      if (elements.statusDisclaimerTag) {
        elements.statusDisclaimerTag.classList.remove('hidden');
        elements.statusDisclaimerTag.textContent = 'Simulated data, not a real tracking source';
      }
      if (elements.simulateControlContainer) elements.simulateControlContainer.classList.remove('hidden');
    }
  }

  async function handleCheckStatusNow() {
    if (!state.currentTripId) return;
    const btn = elements.btnCheckStatusNow;
    setButtonLoading(btn, true);
    state.isStatusRequestInFlight = true;

    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(state.currentTripId)}/status/check`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Status check failed');
      }

      showToast(data.message || 'Status check completed', 'info');

      if (data.message && data.message.includes('No bookings start within 24 hours')) {
        if (elements.statusWindowNote) {
          elements.statusWindowNote.textContent = data.message;
          elements.statusWindowNote.classList.remove('hidden');
        }
      }

      // Reload trip to refresh timeline chips, disruptions, banner, and audit entries
      await loadTripById(state.currentTripId);

    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(btn, false);
      state.isStatusRequestInFlight = false;
    }
  }

  async function handleApplySimulation() {
    const bookingId = elements.selectSimulateBooking ? elements.selectSimulateBooking.value : '';
    if (!bookingId) {
      showToast('Please select a booking to simulate', 'warning');
      return;
    }

    const statusState = elements.selectSimulateState ? elements.selectSimulateState.value : 'DELAYED';
    const delayMinutes = parseInt(elements.inputSimulateDelay ? elements.inputSimulateDelay.value : '90', 10) || 0;

    const btn = elements.btnApplySimulation;
    setButtonLoading(btn, true);
    state.isStatusRequestInFlight = true;

    try {
      const res = await fetch('/api/simulate/status', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tripId: state.currentTripId, bookingId, state: statusState, delayMinutes })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to update simulated status');
      }

      showToast(data.message || 'Simulated status set', 'success');
      // Reload trip to reflect simulated status chips
      await loadTripById(state.currentTripId);

    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(btn, false);
      state.isStatusRequestInFlight = false;
    }
  }

  // Report Problem Handlers
  function openReportProblemModal(bkgId) {
    if (!state.trip || !state.trip.bookings) return;
    const booking = state.trip.bookings.find((b) => b.id === bkgId);
    if (!booking) return;

    if (elements.reportBookingId) elements.reportBookingId.value = booking.id;
    if (elements.reportBookingSummary) {
      elements.reportBookingSummary.innerHTML = `
        <strong>[${escapeHtml(booking.type)}] ${escapeHtml(booking.provider)}</strong>
        <div class="text-xs text-muted" style="margin-top: 4px;">
          ${escapeHtml(booking.from || '-')} &rarr; ${escapeHtml(booking.to || '-')} |
          Scheduled: ${formatDateTime(booking.startTime)} - ${formatDateTime(booking.endTime)}
        </div>
      `;
    }

    if (elements.reportProblemType) elements.reportProblemType.value = 'DELAYED';
    if (elements.groupReportDelay) elements.groupReportDelay.classList.remove('hidden');
    if (elements.reportDelayMinutes) elements.reportDelayMinutes.value = 60;
    if (elements.reportProblemNote) elements.reportProblemNote.value = '';

    if (elements.modalReportProblem) elements.modalReportProblem.classList.remove('hidden');
  }

  function closeReportProblemModal() {
    if (elements.modalReportProblem) elements.modalReportProblem.classList.add('hidden');
  }

  async function handleSubmitReportProblem(e) {
    e.preventDefault();
    const tripId = state.currentTripId;
    const bookingId = elements.reportBookingId ? elements.reportBookingId.value : '';
    const type = elements.reportProblemType ? elements.reportProblemType.value : 'DELAYED';
    const delayMinutes = type === 'CANCELLED' ? 0 : parseInt(elements.reportDelayMinutes ? elements.reportDelayMinutes.value : '60', 10) || 0;
    const note = elements.reportProblemNote ? elements.reportProblemNote.value.trim() : '';

    if (!tripId || !bookingId) return;

    const btn = elements.btnSubmitReportProblem;
    setButtonLoading(btn, true);

    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/bookings/${encodeURIComponent(bookingId)}/report-problem`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type, delayMinutes, note })
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to submit problem report');
      }

      closeReportProblemModal();
      showToast(`Problem reported on booking ${bookingId} (${type}). Impact evaluated.`, 'warning');
      await loadTripById(tripId);
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(btn, false);
    }
  }

  function startStatusPolling(tripId) {
    stopStatusPolling();
    state.statusPollingTimer = setInterval(async () => {
      if (state.isStatusRequestInFlight) return;
      if (!elements.sectionWorkspace || elements.sectionWorkspace.classList.contains('hidden')) return;
      if (!tripId || state.currentTripId !== tripId) return;

      state.isStatusRequestInFlight = true;
      try {
        const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/status`);
        if (res.ok) {
          const data = await res.json();
          if (data.success && Array.isArray(data.data)) {
            // If any booking has a disruption or updated state, reload workspace
            const hasDisruption = data.data.some((s) => s.state === 'DELAYED' || s.state === 'CANCELLED');
            if (hasDisruption && state.trip && state.trip.status !== 'DISRUPTED') {
              await loadTripById(tripId);
            }
          }
        }
      } catch (ignored) {
      } finally {
        state.isStatusRequestInFlight = false;
      }
    }, 60000);
  }

  function stopStatusPolling() {
    if (state.statusPollingTimer) {
      clearInterval(state.statusPollingTimer);
      state.statusPollingTimer = null;
    }
  }

  // ==========================================
  // Disruption Simulation & BFS Cascade
  // ==========================================

  async function handleInjectDisruption() {
    const bookingId = elements.selectDisruptionBooking.value;
    if (!bookingId) {
      showToast('Please select a booking to disrupt', 'warning');
      return;
    }

    const type = elements.selectDisruptionType.value;
    const delayMinutes = type === 'FLIGHT_DELAY' ? parseInt(elements.inputDelayMinutes.value, 10) || 120 : 0;
    const reason = elements.inputDisruptionReason.value || 'Operational cancellation';

    setButtonLoading(elements.btnInjectDisruption, true);

    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(state.currentTripId)}/disruptions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: type,
          bookingId: bookingId,
          delayMinutes: delayMinutes,
          reason: reason,
        }),
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to inject disruption');
      }

      state.trip = data.data.trip;
      state.disruptionResult = data.data;

      renderWorkspace(state.trip);
      renderDisruptionOutput(data.data);
      loadNotificationHistory(tripId);

      showToast(`Recorded ${type} on ${bookingId}. BFS cascade executed.`, 'danger');
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnInjectDisruption, false);
    }
  }

  function renderDisruptionOutput(data) {
    elements.disruptionOutputContainer.classList.remove('hidden');

    // Render SMS Bubble
    const notif = data.notification;
    if (notif) {
      elements.smsRecipient.textContent = notif.recipient || '+91-9876543210';
      elements.smsMessageBox.innerHTML = escapeHtml(notif.body).replace(/\n/g, '<br>');
    }

    // Render Impact Summary Callout
    const impact = data.impact;
    if (impact) {
      let bannerText = impact.summary || '';
      if (impact.hardCommitmentsBreached && impact.hardCommitmentsBreached.length > 0) {
        bannerText = `Hard commitment at risk: ${impact.hardCommitmentsBreached.join(', ')}`;
      }

      elements.impactSummaryBox.innerHTML = `
        <div class="impact-banner">
          <span class="banner-icon">&#9888;</span>
          <span>${escapeHtml(bannerText)}</span>
        </div>
      `;

      // Render Cascade Cards
      const cascadeContainer = elements.cascadeList;
      cascadeContainer.innerHTML = '';
      const impacted = impact.impactedBookings || [];

      if (impacted.length === 0) {
        cascadeContainer.innerHTML = '<p class="empty-state">No downstream bookings were impacted.</p>';
      } else {
        impacted.forEach((item) => {
          const card = document.createElement('div');
          card.className = 'cascade-card';
          const badgeClass = getStatusBadgeClass(item.status);

          let projectedDisplay = formatDateTime(item.projectedStartTime);
          if (item.status === 'CANCELLED') {
            projectedDisplay = 'Cancelled';
          }

          card.innerHTML = `
            <div class="cascade-card-header">
              <span class="booking-tag">[${escapeHtml(item.bookingType)}] ${escapeHtml(item.provider)}</span>
              <span class="badge ${badgeClass}">${escapeHtml(formatStatus(item.status))}</span>
            </div>
            <div class="cascade-times-row right-aligned">
              <div class="time-block">
                <span class="time-label">Original:</span>
                <span class="time-val">${formatDateTime(item.originalStartTime)}</span>
              </div>
              <span class="arrow">&rarr;</span>
              <div class="time-block">
                <span class="time-label">Projected:</span>
                <span class="time-val ${item.status === 'CANCELLED' ? 'text-danger' : 'text-amber'}">${escapeHtml(projectedDisplay)}</span>
              </div>
            </div>
            <div class="cascade-reason">${escapeHtml(item.reason)}</div>
          `;
          cascadeContainer.appendChild(card);
        });
      }
    }
  }

  // ==========================================
  // Recovery Options Generation & Rendering
  // ==========================================

  async function handleFindRecovery(relaxParams = null) {
    if (!state.trip || state.trip.status !== 'DISRUPTED') {
      showToast('Recovery options can only be searched when a trip is disrupted.', 'warning');
      return;
    }

    setButtonLoading(elements.btnFindRecovery, true);

    if (relaxParams) {
      state.currentRelaxation = { ...state.currentRelaxation, ...relaxParams };
    } else if (relaxParams === null && Object.keys(state.currentRelaxation).length === 0) {
      state.currentRelaxation = {};
    }

    try {
      const queryParams = new URLSearchParams();
      if (state.currentRelaxation.allowOvernight) {
        queryParams.set('allowOvernight', 'true');
      }
      if (state.currentRelaxation.extendDeadlineHours) {
        queryParams.set('extendDeadlineHours', String(state.currentRelaxation.extendDeadlineHours));
      }
      if (state.currentRelaxation.raiseBudgetPercent) {
        queryParams.set('raiseBudgetPercent', String(state.currentRelaxation.raiseBudgetPercent));
      }

      const queryString = queryParams.toString() ? `?${queryParams.toString()}` : '';
      const res = await fetch(`/api/trips/${encodeURIComponent(state.currentTripId)}/recover${queryString}`, {
        method: 'POST',
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to generate recovery options');
      }

      state.recoveryData = data.data;
      renderRecoveryOptions(data.data);
      if (data.data.relaxationApplied) {
        showToast(`Search updated: ${data.data.relaxationApplied}`, 'info');
      } else {
        showToast('Found recovery candidates ranked by deterministic scoring.', 'success');
      }
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnFindRecovery, false);
    }
  }

  function handleRelaxConstraint(action) {
    if (action === 'overnight') {
      handleFindRecovery({ allowOvernight: true });
    } else if (action === 'deadline') {
      const currentHours = state.currentRelaxation.extendDeadlineHours || 0;
      handleFindRecovery({ extendDeadlineHours: currentHours + 3 });
    } else if (action === 'budget') {
      const currentPct = state.currentRelaxation.raiseBudgetPercent || 0;
      handleFindRecovery({ raiseBudgetPercent: currentPct + 25 });
    } else if (action === 'reset') {
      state.currentRelaxation = {};
      handleFindRecovery({});
    }
  }

  function renderRecoveryOptions(recoveryData) {
    const container = elements.optionsCardsContainer;
    container.innerHTML = '';

    // No perfect option banner & relaxation toolbar
    if (recoveryData.noPerfectOption) {
      elements.noPerfectBanner.classList.remove('hidden');
      elements.noPerfectText.textContent = recoveryData.explanation || 'No candidate perfectly satisfies all constraints. Trade-offs are present.';
      if (elements.relaxConstraintsBar) {
        elements.relaxConstraintsBar.classList.remove('hidden');
      }
    } else {
      elements.noPerfectBanner.classList.add('hidden');
      if (elements.relaxConstraintsBar) {
        elements.relaxConstraintsBar.classList.add('hidden');
      }
    }

    if (recoveryData.relaxationApplied) {
      if (elements.relaxationStatusBadge) {
        elements.relaxationStatusBadge.textContent = 'Relaxation active for this search: ' + recoveryData.relaxationApplied;
        elements.relaxationStatusBadge.classList.remove('hidden');
      }
      if (elements.btnRelaxReset) {
        elements.btnRelaxReset.classList.remove('hidden');
      }
    } else {
      if (elements.relaxationStatusBadge) {
        elements.relaxationStatusBadge.classList.add('hidden');
      }
      if (elements.btnRelaxReset) {
        elements.btnRelaxReset.classList.add('hidden');
      }
    }

    if (elements.btnRelaxOvernight) {
      elements.btnRelaxOvernight.classList.toggle('active', !!state.currentRelaxation.allowOvernight);
    }
    if (elements.btnRelaxDeadline) {
      elements.btnRelaxDeadline.classList.toggle('active', !!(state.currentRelaxation.extendDeadlineHours && state.currentRelaxation.extendDeadlineHours > 0));
    }
    if (elements.btnRelaxBudget) {
      elements.btnRelaxBudget.classList.toggle('active', !!(state.currentRelaxation.raiseBudgetPercent && state.currentRelaxation.raiseBudgetPercent > 0));
    }

    const options = recoveryData.options || [];
    if (options.length === 0) {
      container.innerHTML = '<p class="empty-state">Zero recovery candidates survived filtering.</p>';
    } else {
      options.forEach((opt) => {
        const card = document.createElement('div');
        const isSelected = state.selectedOptionId === opt.id;
        card.className = `option-card ${isSelected ? 'selected' : ''}`;
        card.setAttribute('data-option-id', opt.id);

        let labelClass = 'label-safest';
        if (opt.label === 'FASTEST') labelClass = 'label-fastest';
        if (opt.label === 'CHEAPEST') labelClass = 'label-cheapest';

        // Extract legs summary
        const legs = opt.legs || [];
        const mainLeg = legs[0] || {};
        const provider = mainLeg.provider || 'Replacement Transit';
        const route = `${mainLeg.from || 'Origin'} &rarr; ${mainLeg.to || 'Destination'}`;
        const schedule = `${formatDateTime(mainLeg.startTime)} - ${formatDateTime(opt.arrivalTime)}`;

        const isEstimated = opt.source === 'ESTIMATED' || (mainLeg.source === 'ESTIMATED');

        const isBestAvailable = opt.bestAvailableNote || (recoveryData.noPerfectOption && opt.constraintsViolated && opt.constraintsViolated.length > 0);
        const bestAvailableText = opt.bestAvailableNote || ('Best available, breaks: ' + (opt.constraintsViolated[0] || 'Constraint'));

        // Constraints checklist
        let constraintsHtml = '';
        (opt.constraintsMet || []).forEach((c) => {
          constraintsHtml += `<div class="constraint-item met"><span class="check-icon">&#10003;</span> <span>${escapeHtml(c)}</span></div>`;
        });
        (opt.constraintsViolated || []).forEach((c) => {
          constraintsHtml += `<div class="constraint-item violated"><span class="check-icon">&#10007;</span> <span>${escapeHtml(c)}</span></div>`;
        });

        card.innerHTML = `
          <div class="option-header">
            <span class="option-label-badge ${labelClass}">${escapeHtml(opt.label)}</span>
            <div class="option-header-right">
              ${isEstimated ? '<span class="badge badge-warning badge-estimated">ESTIMATED</span>' : '<span class="badge badge-info">INVENTORY</span>'}
              <span class="option-score">Score: ${opt.score}</span>
            </div>
          </div>
          <div class="option-body">
            ${isBestAvailable ? `
              <div class="option-broken-badge">${escapeHtml(bestAvailableText)}</div>
            ` : ''}
            ${isEstimated ? `
              <div class="estimated-notice">
                <span class="badge badge-warning">ESTIMATED</span>
                <span class="estimated-note">Note: Estimated options are not real timetables.</span>
              </div>
            ` : ''}
            <div class="option-route-info">
              <span class="option-provider">${escapeHtml(provider)}</span>
              <span class="option-route">${route}</span>
              <span class="option-schedule">${schedule}</span>
            </div>
            <div class="option-cost-tag">${formatCurrency(opt.totalCost)}</div>
            <div class="option-explanation">${escapeHtml(opt.explanation)}</div>
            <div class="constraints-checklist">
              ${constraintsHtml}
            </div>
          </div>
          <div class="option-footer">
            <button class="btn btn-primary full-width btn-select-option" data-option-id="${escapeHtml(opt.id)}">
              <span class="btn-text">${isSelected ? 'Option Selected' : 'Select this option'}</span>
              <span class="spinner" aria-hidden="true"></span>
            </button>
          </div>
        `;

        container.appendChild(card);
      });

      // Selection handler is bound once via delegation on elements.optionsCardsContainer
    }

    // Rejected Options (sorted closest to feasible)
    const rejected = recoveryData.rejectedOptions || [];
    const rejCount = rejected.length;
    elements.rejectedCount.textContent = rejCount === 1 ? '1 option' : `${rejCount} options`;
    if (rejCount > 0) {
      elements.rejectedOptionsWrapper.classList.remove('hidden');
      elements.rejectedOptionsList.innerHTML = '';
      rejected.forEach((item) => {
        const div = document.createElement('div');
        div.className = 'rejected-item';
        const blockedBadge = item.blockedBy
          ? `<span class="rejected-blocked-badge">Blocked by: ${escapeHtml(item.blockedBy)}</span>`
          : '';
        div.innerHTML = `
          <div class="rejected-name">[${escapeHtml(item.mode)}] ${escapeHtml(item.provider)} (${formatCurrency(item.cost)}) ${blockedBadge}</div>
          <div class="rejected-reason">${escapeHtml(item.reason)}</div>
        `;
        elements.rejectedOptionsList.appendChild(div);
      });
    } else {
      elements.rejectedOptionsWrapper.classList.add('hidden');
    }
  }

  async function handleSelectOption(optionId, button) {
    if (button && button.disabled) return;
    const card = button ? button.closest('.option-card') : null;
    const cardOptionId = card ? card.getAttribute('data-option-id') : null;
    const resolvedOptionId = cardOptionId || optionId || state.selectedOptionId;
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);

    state.selectedOptionId = resolvedOptionId;

    // Update active visual state on cards
    elements.optionsCardsContainer.querySelectorAll('.option-card').forEach((c) => {
      const isCardSelected = c.getAttribute('data-option-id') === resolvedOptionId;
      c.classList.toggle('selected', isCardSelected);
      const btn = c.querySelector('.btn-select-option .btn-text');
      if (btn) {
        btn.textContent = isCardSelected ? 'Option Selected' : 'Select this option';
      }
    });

    if (button) setButtonLoading(button, true);

    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/plan`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ optionId: resolvedOptionId }),
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to compose recovery plan');
      }

      state.planData = data.data;
      renderPlan(data.data);
      loadNotificationHistory(tripId);
      showToast(`Composed recovery plan for ${resolvedOptionId}`, 'info');

      // Scroll smoothly to plan section
      elements.sectionPlan.scrollIntoView({ behavior: 'smooth' });
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      if (button) setButtonLoading(button, false);
    }
  }

  function renderPlan(planData) {
    if (!planData) {
      elements.sectionPlan.classList.add('hidden');
      return;
    }

    elements.sectionPlan.classList.remove('hidden');

    // Trade-off Rationale
    elements.tradeoffBox.textContent = planData.tradeOffExplanation || 'Deterministic trade-off evaluation computed across budget, deadline, and safety buffers.';

    // Before vs After Diff Table
    const diffBody = elements.diffTableBody;
    diffBody.innerHTML = '';
    const diffs = planData.itineraryDiff || [];

    diffs.forEach((d) => {
      const row = document.createElement('tr');
      const oldStatusClass = getStatusBadgeClass(d.oldStatus);
      const newStatusClass = getStatusBadgeClass(d.newStatus);

      row.innerHTML = `
        <td><strong>[${escapeHtml(d.type)}]</strong> ${escapeHtml(d.provider)}</td>
        <td>${formatDateTime(d.oldStart)}</td>
        <td>${d.oldStatus ? `<span class="badge ${oldStatusClass}">${escapeHtml(formatStatus(d.oldStatus))}</span>` : '-'}</td>
        <td class="font-bold text-gold">${formatDateTime(d.newStart)}</td>
        <td><span class="badge ${newStatusClass}">${escapeHtml(formatStatus(d.newStatus))}</span></td>
        <td>${escapeHtml(d.changeDescription)}</td>
      `;
      diffBody.appendChild(row);
    });

    // Net Financial Breakdown
    const costContainer = elements.costLinesContainer;
    costContainer.innerHTML = '';
    const costBreakdown = planData.costBreakdown || {};
    const lines = costBreakdown.lines || [];

    lines.forEach((line) => {
      const row = document.createElement('div');
      row.className = `cost-line-row ${line.isDeduction ? 'deduction' : ''}`;
      row.innerHTML = `
        <div class="cost-line-info">
          <span class="cost-line-label">${escapeHtml(line.label)}</span>
          <span class="cost-line-note">${escapeHtml(line.assumptionNote || '')}</span>
        </div>
        <span class="cost-line-amount ${line.isDeduction ? 'text-green' : ''}">
          ${line.isDeduction ? '-' : '+'}${formatCurrency(Math.abs(line.amount))}
        </span>
      `;
      costContainer.appendChild(row);
    });

    elements.costNetTotal.textContent = formatCurrency(costBreakdown.netTripCost);
    elements.costMaxBudget.textContent = formatCurrency(costBreakdown.maxBudget);
    elements.costRemainingBudget.textContent = formatCurrency(costBreakdown.remainingBudget);

    // Checklist
    const checklistContainer = elements.checklistContainer;
    checklistContainer.innerHTML = '';
    const items = planData.checklist || [];

    items.forEach((item) => {
      const li = document.createElement('li');
      li.className = 'checklist-item';
      const isChecked = state.checklistState[item.id] !== undefined ? state.checklistState[item.id] : item.done;

      li.innerHTML = `
        <label class="checklist-label">
          <input type="checkbox" data-item-id="${escapeHtml(item.id)}" ${isChecked ? 'checked' : ''}>
          <span class="checklist-text ${isChecked ? 'completed' : ''}">${escapeHtml(item.text)}</span>
        </label>
      `;

      const checkbox = li.querySelector('input');
      checkbox.addEventListener('change', (e) => {
        state.checklistState[item.id] = e.target.checked;
        li.querySelector('.checklist-text').classList.toggle('completed', e.target.checked);
      });

      checklistContainer.appendChild(li);
    });

    // Hotel Message Box (Absent when there is no HOTEL booking)
    const hasHotel = state.trip && state.trip.bookings && state.trip.bookings.some(b => b.type === 'HOTEL');
    if (elements.planHotelMessageSection) {
      if (hasHotel && planData.hotelMessage && planData.hotelMessage.trim().length > 0) {
        elements.planHotelMessageSection.classList.remove('hidden');
        elements.hotelMessageBox.textContent = planData.hotelMessage;

        const hotelBooking = state.trip.bookings.find(b => b.type === 'HOTEL');
        const hotelEmail = (hotelBooking && hotelBooking.hotelContactEmail) ? hotelBooking.hotelContactEmail.trim() : '';
        const isSmtpActive = state.channelStatus && state.channelStatus.email === 'Active';

        if (elements.hotelMessageRecipientInfo) {
          elements.hotelMessageRecipientInfo.textContent = hotelEmail ? `Hotel Email: ${hotelEmail}` : 'No hotel contact email specified';
        }

        if (hotelEmail && isSmtpActive && elements.btnSendHotelEmail) {
          elements.btnSendHotelEmail.classList.remove('hidden');
          if (elements.linkMailtoHotel) elements.linkMailtoHotel.classList.add('hidden');
        } else if (elements.linkMailtoHotel) {
          if (elements.btnSendHotelEmail) elements.btnSendHotelEmail.classList.add('hidden');
          elements.linkMailtoHotel.classList.remove('hidden');
          const subject = encodeURIComponent('Urgent: Reservation Update for Traveller ' + (state.trip.travellerName || 'Guest'));
          const body = encodeURIComponent(planData.hotelMessage);
          elements.linkMailtoHotel.href = `mailto:${hotelEmail}?subject=${subject}&body=${body}`;
        }
      } else {
        elements.planHotelMessageSection.classList.add('hidden');
        elements.hotelMessageBox.textContent = '';
      }
    }
  }

  // ==========================================
  // Plan Approval / Rejection Gate
  // ==========================================

  async function handleApprovePlan() {
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    const optionId = state.selectedOptionId;
    if (!tripId || !optionId) {
      showToast('Please select a recovery option first', 'warning');
      return;
    }

    setButtonLoading(elements.btnApprovePlan, true);

    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ optionId }),
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to approve recovery plan');
      }

      showToast('Recovery plan APPROVED! Itinerary rebooked and trip status set to RESOLVED.', 'success');

      // Refresh trip state from server
      await loadTripById(tripId);
      elements.sectionResolvedBanner.scrollIntoView({ behavior: 'smooth' });
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnApprovePlan, false);
    }
  }

  async function handleRejectPlan() {
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    const optionId = state.selectedOptionId;
    if (!tripId || !optionId) {
      showToast('No option selected', 'warning');
      return;
    }

    setButtonLoading(elements.btnRejectPlan, true);

    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ optionId }),
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to reject recovery plan');
      }

      state.trip = data.data;
      elements.sectionPlan.classList.add('hidden');
      renderWorkspace(state.trip);
      showToast('Recovery plan rejected. Trip remains in DISRUPTED status.', 'warning');
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnRejectPlan, false);
    }
  }

  // ==========================================
  // Phase 8: Notifications & Dispatch Engine
  // ==========================================

  async function loadChannelStatus() {
    try {
      const res = await fetch('/api/notifications/status');
      if (!res.ok) return;
      const data = await res.json();
      if (!data.success || !data.data) return;

      state.channelStatus = data.data;

      if (elements.badgeChannelEmail) {
        const isAct = data.data.email === 'Active';
        elements.badgeChannelEmail.textContent = data.data.email;
        elements.badgeChannelEmail.className = `badge ${isAct ? 'badge-success' : 'badge-warning'}`;
      }

      if (elements.badgeChannelSms) {
        const isAct = data.data.sms === 'Active';
        elements.badgeChannelSms.textContent = data.data.sms;
        elements.badgeChannelSms.className = `badge ${isAct ? 'badge-success' : 'badge-warning'}`;
      }

      if (elements.badgeChannelWhatsapp) {
        const val = data.data.whatsapp;
        elements.badgeChannelWhatsapp.textContent = val;
        elements.badgeChannelWhatsapp.className = `badge ${val === 'Active' ? 'badge-success' : val === 'Simulated' ? 'badge-warning' : 'badge-neutral'}`;
      }
    } catch (err) {
      console.warn('Failed to load notification channel status:', err);
    }
  }

  async function loadNotificationHistory(tripId) {
    if (!tripId || !elements.notificationsTableBody) return;
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/notifications`);
      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || 'Failed to load notifications history');
      }
      const data = await res.json();
      if (data.success && data.data) {
        renderNotificationHistory(data.data);
      }
    } catch (err) {
      console.warn('Failed to load notification history:', err);
    }
  }

  function renderNotificationHistory(history) {
    if (!elements.notificationsTableBody) return;
    if (!history || history.length === 0) {
      elements.notificationsTableBody.innerHTML = `
        <tr>
          <td colspan="6" class="empty-state">No alerts dispatched yet for this trip.</td>
        </tr>
      `;
      return;
    }

    elements.notificationsTableBody.innerHTML = history.map(item => {
      const timeStr = formatDateTime(item.createdAt);
      let chipClass = 'chip-simulated';
      if (item.status === 'SENT') chipClass = 'chip-sent';
      else if (item.status === 'FAILED') chipClass = 'chip-failed';
      else if (item.status === 'SKIPPED') chipClass = 'chip-skipped';

      const errorNote = item.error ? `<div class="field-error" style="margin-top: 4px; font-size: 0.78rem;">${escapeHtml(item.error)}</div>` : '';

      return `
        <tr>
          <td><span class="mono">${escapeHtml(timeStr)}</span></td>
          <td><span class="badge badge-neutral">${escapeHtml(item.eventType)}</span></td>
          <td><strong>${escapeHtml(item.channel)}</strong></td>
          <td><span class="mono">${escapeHtml(item.recipientMasked || '')}</span></td>
          <td><span class="status-chip ${chipClass}">${escapeHtml(item.status)}</span></td>
          <td>
            <div>${escapeHtml(item.subject || '')}</div>
            ${errorNote}
          </td>
        </tr>
      `;
    }).join('');
  }

  async function handleSendTestAlert() {
    const tripId = state.currentTripId;
    if (!tripId) {
      showToast('Please load an active trip first', 'warning');
      return;
    }
    setButtonLoading(elements.btnSendTestAlert, true);
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/notifications/test`, {
        method: 'POST'
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to send test alert');
      }
      showToast('Test alert dispatched on enabled channels', 'success');
      await loadNotificationHistory(tripId);
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnSendTestAlert, false);
    }
  }

  async function handleSendHotelEmail() {
    const tripId = state.currentTripId;
    if (!tripId) return;

    if (!confirm('Are you sure you want to send this reservation update email to the hotel?')) {
      return;
    }

    setButtonLoading(elements.btnSendHotelEmail, true);
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/plan/hotel-message/send`, {
        method: 'POST'
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to send hotel email');
      }
      showToast('Hotel update email sent successfully!', 'success');
      await loadNotificationHistory(tripId);
      await loadTripById(tripId);
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnSendHotelEmail, false);
    }
  }

  // ==========================================
  // Audit Trail Rendering
  // ==========================================

  function renderAuditLog(auditLog) {
    const tbody = elements.auditTableBody;
    const count = auditLog.length;
    elements.auditCountBadge.textContent = count === 1 ? '1 entry' : `${count} entries`;

    if (count === 0) {
      tbody.innerHTML = '<tr><td colspan="4" class="empty-state">No audit records logged yet.</td></tr>';
      return;
    }

    tbody.innerHTML = '';
    // Reverse chronological for audit trail
    const sorted = [...auditLog].reverse();

    sorted.forEach((entry) => {
      const tr = document.createElement('tr');
      let actorBadge = 'badge-neutral';
      if (entry.actor === 'AGENT') actorBadge = 'badge-purple';
      if (entry.actor === 'USER') actorBadge = 'badge-gold';
      if (entry.actor === 'SYSTEM') actorBadge = 'badge-info';

      tr.innerHTML = `
        <td class="font-mono text-dim">${formatDateTime(entry.timestamp)}</td>
        <td><span class="badge ${actorBadge}">${escapeHtml(entry.actor)}</span></td>
        <td><strong>${escapeHtml(entry.action)}</strong></td>
        <td class="audit-details-cell">${escapeHtml(entry.details)}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  // ==========================================
  // Booking Management (Add, Edit, Delete)
  // ==========================================

  function openBookingEditor(bookingId = null) {
    if (!state.trip) {
      showToast('Please load a trip first', 'warning');
      return;
    }

    // Reset error labels
    elements.errBookingProvider.textContent = '';
    elements.errBookingStart.textContent = '';
    elements.errBookingEnd.textContent = '';
    elements.errBookingCost.textContent = '';
    elements.errBookingCycle.textContent = '';

    const bookings = state.trip.bookings || [];

    if (bookingId) {
      // Edit mode
      const b = bookings.find((item) => item.id === bookingId);
      if (!b) return;

      elements.modalBookingTitle.textContent = `Edit Booking: ${b.id}`;
      elements.editBookingId.value = b.id;
      elements.bookingInputType.value = b.type || 'FLIGHT';
      elements.bookingInputProvider.value = b.provider || '';
      elements.bookingInputFrom.value = b.from || '';
      elements.bookingInputTo.value = b.to || '';
      elements.bookingInputStart.value = b.startTime ? b.startTime.substring(0, 16) : '';
      elements.bookingInputEnd.value = b.endTime ? b.endTime.substring(0, 16) : '';
      elements.bookingInputCost.value = b.cost || 0;
      elements.bookingInputStatus.value = b.status || 'CONFIRMED';
      if (elements.bookingInputHotelEmail) {
        elements.bookingInputHotelEmail.value = b.hotelContactEmail || '';
      }

      // Populate dependencies
      populateDependencyCheckboxes(bookings, bookingId, b.dependsOn || []);
    } else {
      // Add mode
      elements.modalBookingTitle.textContent = 'Add New Booking';
      elements.editBookingId.value = '';
      elements.bookingInputType.value = 'FLIGHT';
      elements.bookingInputProvider.value = '';
      elements.bookingInputFrom.value = '';
      elements.bookingInputTo.value = '';
      if (elements.bookingInputHotelEmail) {
        elements.bookingInputHotelEmail.value = '';
      }

      const now = new Date();
      now.setMinutes(0, 0, 0);
      now.setHours(now.getHours() + 1);
      const end = new Date(now.getTime() + 2 * 3600 * 1000);

      elements.bookingInputStart.value = now.toISOString().substring(0, 16);
      elements.bookingInputEnd.value = end.toISOString().substring(0, 16);
      elements.bookingInputCost.value = '1500';
      elements.bookingInputStatus.value = 'CONFIRMED';

      populateDependencyCheckboxes(bookings, null, []);
    }

    elements.modalBookingEditor.classList.remove('hidden');
  }

  function populateDependencyCheckboxes(allBookings, currentBookingId, selectedDeps) {
    const picker = elements.bookingDependenciesPicker;
    picker.innerHTML = '';

    const eligible = allBookings.filter((b) => b.id !== currentBookingId);
    if (eligible.length === 0) {
      picker.innerHTML = '<span class="text-dim">No other bookings exist in this trip.</span>';
      return;
    }

    eligible.forEach((b) => {
      const label = document.createElement('label');
      label.className = 'dep-checkbox-item';
      const isChecked = selectedDeps.includes(b.id);

      label.innerHTML = `
        <input type="checkbox" name="depBooking" value="${escapeHtml(b.id)}" ${isChecked ? 'checked' : ''}>
        <span>[${escapeHtml(b.type)}] ${escapeHtml(b.provider)} (${escapeHtml(b.id)})</span>
      `;
      picker.appendChild(label);
    });
  }

  async function handleSaveBooking(e) {
    e.preventDefault();

    const bookingId = elements.editBookingId.value;
    const isEdit = Boolean(bookingId);

    const type = elements.bookingInputType.value;
    const provider = elements.bookingInputProvider.value.trim();
    const from = elements.bookingInputFrom.value.trim();
    const to = elements.bookingInputTo.value.trim();
    const startTimeStr = elements.bookingInputStart.value;
    const endTimeStr = elements.bookingInputEnd.value;
    const cost = parseFloat(elements.bookingInputCost.value) || 0;
    const status = elements.bookingInputStatus.value;

    // Selected dependencies
    const selectedDeps = [];
    elements.bookingDependenciesPicker.querySelectorAll('input[type="checkbox"]:checked').forEach((cb) => {
      selectedDeps.push(cb.value);
    });

    // Client-side validations
    let hasError = false;
    if (!provider) {
      elements.errBookingProvider.textContent = 'Provider name is required';
      hasError = true;
    }
    if (!startTimeStr || !endTimeStr) {
      elements.errBookingStart.textContent = 'Start and end times are required';
      hasError = true;
    } else if (new Date(endTimeStr) <= new Date(startTimeStr)) {
      elements.errBookingEnd.textContent = 'End time must be strictly after start time';
      hasError = true;
    }

    if (hasError) return;

    const hotelEmail = elements.bookingInputHotelEmail ? elements.bookingInputHotelEmail.value.trim() : null;

    const payload = {
      type: type,
      provider: provider,
      from: from,
      to: to,
      startTime: startTimeStr.length === 16 ? startTimeStr + ':00' : startTimeStr,
      endTime: endTimeStr.length === 16 ? endTimeStr + ':00' : endTimeStr,
      cost: cost,
      status: status,
      dependsOn: selectedDeps,
      hotelContactEmail: hotelEmail || null,
    };

    const url = isEdit
      ? `/api/trips/${encodeURIComponent(state.currentTripId)}/bookings/${encodeURIComponent(bookingId)}`
      : `/api/trips/${encodeURIComponent(state.currentTripId)}/bookings`;

    const method = isEdit ? 'PUT' : 'POST';

    try {
      const res = await fetch(url, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to save booking');
      }

      state.trip = data.data;
      renderWorkspace(state.trip);
      elements.modalBookingEditor.classList.add('hidden');
      showToast(isEdit ? `Booking ${bookingId} updated` : 'New booking added', 'success');
    } catch (err) {
      showToast(err.message, 'danger');
      if (err.message.includes('cycle')) {
        elements.errBookingCycle.textContent = 'Dependency cycle detected! A booking cannot depend in a closed loop.';
      } else {
        elements.errBookingEnd.textContent = err.message;
      }
    }
  }

  async function handleDeleteBooking(bookingId) {
    if (!confirm(`Remove booking ${bookingId} from this trip?`)) return;

    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(state.currentTripId)}/bookings/${encodeURIComponent(bookingId)}`, {
        method: 'DELETE',
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to delete booking');
      }

      state.trip = data.data;
      renderWorkspace(state.trip);
      showToast(`Removed booking ${bookingId}`, 'info');
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  // ==========================================
  // New Trip Creation Modal, Booking Builder & Email Parser
  // ==========================================

  const createFormState = {
    hasAttemptedSubmit: false,
    touchedFields: new Set(),
    rowCounter: 0,
  };

  function addCommitmentRow(name = '', time = '', location = '') {
    if (elements.createCommitmentsEmpty) {
      elements.createCommitmentsEmpty.classList.add('hidden');
    }
    const row = document.createElement('div');
    row.className = 'commitment-row';
    row.innerHTML = `
      <input type="text" class="form-control commitment-name" placeholder="Name (e.g. Keynote)" value="${escapeHtml(name)}" required>
      <input type="datetime-local" class="form-control commitment-time" value="${escapeHtml(time)}" required>
      <input type="text" class="form-control commitment-location" placeholder="Location (e.g. Goa)" value="${escapeHtml(location)}">
      <button type="button" class="btn-remove-commitment" title="Remove milestone" aria-label="Remove milestone">&times;</button>
    `;
    row.querySelector('.btn-remove-commitment').addEventListener('click', () => {
      row.remove();
      if (elements.createCommitmentsList.children.length === 0) {
        elements.createCommitmentsEmpty.classList.remove('hidden');
      }
    });
    elements.createCommitmentsList.appendChild(row);
  }

  function addBookingBuilderRow(bData = null) {
    if (elements.createBuilderEmpty) {
      elements.createBuilderEmpty.classList.add('hidden');
    }

    createFormState.rowCounter++;
    const rowId = 'BKG-NEW-' + String(createFormState.rowCounter).padStart(3, '0');

    // Default dates: tomorrow 10:00 to 12:00 if none provided
    const baseDate = new Date();
    baseDate.setDate(baseDate.getDate() + 1);
    const yyyy = baseDate.getFullYear();
    const mm = String(baseDate.getMonth() + 1).padStart(2, '0');
    const dd = String(baseDate.getDate()).padStart(2, '0');
    const defaultStartStr = `${yyyy}-${mm}-${dd}T10:00`;
    const defaultEndStr = `${yyyy}-${mm}-${dd}T12:00`;

    const bType = (bData && bData.type) ? bData.type : 'BUS';
    const bProvider = (bData && bData.provider) ? bData.provider : '';
    const bFrom = (bData && bData.from) ? bData.from : '';
    const bTo = (bData && bData.to) ? bData.to : '';
    const bStart = (bData && bData.startTime) ? String(bData.startTime).substring(0, 16) : defaultStartStr;
    const bEnd = (bData && bData.endTime) ? String(bData.endTime).substring(0, 16) : defaultEndStr;
    const bCost = (bData && bData.cost != null) ? bData.cost : 650;
    const bDepends = (bData && Array.isArray(bData.dependsOn)) ? bData.dependsOn : [];

    const card = document.createElement('div');
    card.className = 'booking-builder-card';
    card.setAttribute('data-row-id', rowId);

    card.innerHTML = `
      <div class="builder-card-top-row">
        <div class="builder-badge-group">
          <span class="builder-row-index-tag">Booking #<span class="builder-idx-num"></span></span>
          <span class="builder-row-title-preview">[${escapeHtml(bType)}] ${escapeHtml(bProvider || 'New Booking')}</span>
        </div>
        <button type="button" class="btn-remove-builder-row" title="Remove this booking" aria-label="Remove booking">&times;</button>
      </div>

      <div class="builder-card-inputs-grid-1">
        <div>
          <label class="builder-field-label">Type</label>
          <select class="form-control builder-input-sm bkg-row-type">
            <option value="BUS" ${bType === 'BUS' ? 'selected' : ''}>Bus</option>
            <option value="CAB" ${bType === 'CAB' ? 'selected' : ''}>Cab / Taxi</option>
            <option value="TRAIN" ${bType === 'TRAIN' ? 'selected' : ''}>Train</option>
            <option value="FLIGHT" ${bType === 'FLIGHT' ? 'selected' : ''}>Flight</option>
            <option value="HOTEL" ${bType === 'HOTEL' ? 'selected' : ''}>Hotel</option>
            <option value="EVENT" ${bType === 'EVENT' ? 'selected' : ''}>Event</option>
          </select>
        </div>
        <div>
          <label class="builder-field-label">Provider / Name *</label>
          <input type="text" class="form-control builder-input-sm bkg-row-provider" placeholder="e.g. KSRTC Sarige" value="${escapeHtml(bProvider)}" required>
        </div>
        <div>
          <label class="builder-field-label">Origin (From) *</label>
          <input type="text" class="form-control builder-input-sm bkg-row-from" placeholder="e.g. Hubballi" value="${escapeHtml(bFrom)}" required>
        </div>
        <div>
          <label class="builder-field-label">Destination (To) *</label>
          <input type="text" class="form-control builder-input-sm bkg-row-to" placeholder="e.g. Goa" value="${escapeHtml(bTo)}" required>
        </div>
      </div>

      <div class="builder-card-inputs-grid-2">
        <div>
          <label class="builder-field-label">Departure / Start *</label>
          <input type="datetime-local" class="form-control builder-input-sm bkg-row-start" value="${escapeHtml(bStart)}" required>
        </div>
        <div>
          <label class="builder-field-label">Arrival / End *</label>
          <input type="datetime-local" class="form-control builder-input-sm bkg-row-end" value="${escapeHtml(bEnd)}" required>
        </div>
        <div>
          <label class="builder-field-label">Cost (INR) *</label>
          <input type="number" class="form-control builder-input-sm bkg-row-cost" min="0" step="10" value="${escapeHtml(String(bCost))}" required>
        </div>
        <div>
          <label class="builder-field-label">Depends On</label>
          <select class="form-control builder-input-sm bkg-row-depends" data-initial-depends="${escapeHtml(JSON.stringify(bDepends))}">
            <option value="">None (Independent start)</option>
          </select>
        </div>
      </div>
    `;

    // Live update preview title on input
    const typeSelect = card.querySelector('.bkg-row-type');
    const providerInput = card.querySelector('.bkg-row-provider');
    const fromInput = card.querySelector('.bkg-row-from');
    const toInput = card.querySelector('.bkg-row-to');
    const titlePreview = card.querySelector('.builder-row-title-preview');
    const startInput = card.querySelector('.bkg-row-start');

    const updateTitle = () => {
      const p = providerInput.value.trim() || 'New Booking';
      const f = fromInput.value.trim();
      const t = toInput.value.trim();
      const route = (f && t) ? ` (${f} \u2192 ${t})` : '';
      titlePreview.textContent = `[${typeSelect.value}] ${p}${route}`;
      refreshDependsOnOptions();
    };

    typeSelect.addEventListener('change', updateTitle);
    providerInput.addEventListener('input', updateTitle);
    fromInput.addEventListener('input', updateTitle);
    toInput.addEventListener('input', updateTitle);

    startInput.addEventListener('change', () => {
      if (createFormState.touchedFields.has('deadline') || createFormState.hasAttemptedSubmit) {
        validateCreateField('deadline');
      }
    });

    // Remove row button
    card.querySelector('.btn-remove-builder-row').addEventListener('click', () => {
      card.remove();
      const remainingCards = elements.createBookingsBuilderList.querySelectorAll('.booking-builder-card');
      elements.createBuilderCount.textContent = remainingCards.length;
      if (remainingCards.length === 0 && elements.createBuilderEmpty) {
        elements.createBuilderEmpty.classList.remove('hidden');
      }
      refreshDependsOnOptions();
      if (createFormState.touchedFields.has('deadline') || createFormState.hasAttemptedSubmit) {
        validateCreateField('deadline');
      }
    });

    elements.createBookingsBuilderList.appendChild(card);
    refreshDependsOnOptions();

    const count = elements.createBookingsBuilderList.querySelectorAll('.booking-builder-card').length;
    elements.createBuilderCount.textContent = count;
    elements.errCreateBookings.textContent = '';
  }

  function refreshDependsOnOptions() {
    const cards = Array.from(elements.createBookingsBuilderList.querySelectorAll('.booking-builder-card'));

    // Update index numbers in header badges
    cards.forEach((card, idx) => {
      const idxSpan = card.querySelector('.builder-idx-num');
      if (idxSpan) idxSpan.textContent = idx + 1;
    });

    // Build items list
    const items = cards.map((c, idx) => {
      const id = c.getAttribute('data-row-id');
      const type = c.querySelector('.bkg-row-type').value;
      const provider = c.querySelector('.bkg-row-provider').value.trim() || 'Booking';
      const from = c.querySelector('.bkg-row-from').value.trim();
      const to = c.querySelector('.bkg-row-to').value.trim();
      const route = (from && to) ? ` (${from} \u2192 ${to})` : '';
      return { id, label: `#${idx + 1}: [${type}] ${provider}${route}` };
    });

    cards.forEach((card) => {
      const selfId = card.getAttribute('data-row-id');
      const select = card.querySelector('.bkg-row-depends');
      const currentSelected = select.value;
      const initialDependsStr = select.getAttribute('data-initial-depends');
      let initialId = null;
      if (initialDependsStr) {
        try {
          const arr = JSON.parse(initialDependsStr);
          if (Array.isArray(arr) && arr.length > 0) initialId = arr[0];
        } catch (e) { }
      }

      select.innerHTML = '<option value="">None (Independent start)</option>';
      items.forEach((item) => {
        if (item.id === selfId) return; // Cannot depend on self
        const opt = document.createElement('option');
        opt.value = item.id;
        opt.textContent = item.label;
        if (currentSelected === item.id || (!currentSelected && initialId === item.id)) {
          opt.selected = true;
        }
        select.appendChild(opt);
      });
    });
  }

  function collectBookingsFromBuilder() {
    const cards = Array.from(elements.createBookingsBuilderList.querySelectorAll('.booking-builder-card'));
    return cards.map((card) => {
      const rowId = card.getAttribute('data-row-id');
      const type = card.querySelector('.bkg-row-type').value;
      const provider = card.querySelector('.bkg-row-provider').value.trim();
      const from = card.querySelector('.bkg-row-from').value.trim();
      const to = card.querySelector('.bkg-row-to').value.trim();
      const sVal = card.querySelector('.bkg-row-start').value;
      const eVal = card.querySelector('.bkg-row-end').value;
      const costVal = parseFloat(card.querySelector('.bkg-row-cost').value) || 0;
      const dependsVal = card.querySelector('.bkg-row-depends').value;

      return {
        id: rowId,
        type: type,
        provider: provider,
        from: from,
        to: to,
        startTime: sVal ? (sVal.length === 16 ? sVal + ':00' : sVal) : null,
        endTime: eVal ? (eVal.length === 16 ? eVal + ':00' : eVal) : null,
        cost: costVal,
        dependsOn: dependsVal ? [dependsVal] : [],
        status: 'CONFIRMED',
      };
    });
  }

  function validateCreateField(field) {
    // Only display error if touched or after first submit attempt
    if (!createFormState.touchedFields.has(field) && !createFormState.hasAttemptedSubmit) {
      return true;
    }

    if (field === 'title') {
      const val = elements.createTripTitle.value.trim();
      if (!val) {
        elements.errCreateTitle.textContent = 'Trip title cannot be blank';
        return false;
      }
      elements.errCreateTitle.textContent = '';
      return true;
    }

    if (field === 'phone') {
      const rawPhone = elements.createTripPhone.value.trim();
      let cleanPhone = rawPhone.replace(/\D/g, '');
      if (cleanPhone.length === 12 && cleanPhone.startsWith('91')) {
        cleanPhone = cleanPhone.substring(2);
      } else if (cleanPhone.length === 11 && cleanPhone.startsWith('0')) {
        cleanPhone = cleanPhone.substring(1);
      }
      if (cleanPhone.length !== 10) {
        elements.errCreatePhone.textContent = 'Phone number must be exactly 10 digits (e.g. 9876543210)';
        return false;
      }
      elements.errCreatePhone.textContent = '';
      return true;
    }

    if (field === 'email') {
      const rawEmail = elements.createTripEmail ? elements.createTripEmail.value.trim() : '';
      if (rawEmail) {
        const emailRegex = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
        if (!emailRegex.test(rawEmail)) {
          elements.errCreateEmail.textContent = 'Please enter a valid email address format (e.g. traveller@example.com)';
          return false;
        }
      }
      if (elements.errCreateEmail) elements.errCreateEmail.textContent = '';
      return true;
    }

    if (field === 'budget') {
      const budgetVal = parseFloat(elements.createTripBudget.value);
      if (!budgetVal || budgetVal <= 0) {
        elements.errCreateBudget.textContent = 'Budget must be positive (> 0)';
        return false;
      }
      elements.errCreateBudget.textContent = '';
      return true;
    }

    if (field === 'deadline') {
      const deadlineStr = elements.createTripDeadline.value;
      if (!deadlineStr) {
        elements.errCreateDeadline.textContent = 'Must arrive by deadline is required';
        return false;
      }
      const bookings = collectBookingsFromBuilder();
      let earliestBookingStart = null;
      bookings.forEach((b) => {
        if (b.startTime) {
          const bTime = new Date(b.startTime);
          if (!earliestBookingStart || bTime < earliestBookingStart) {
            earliestBookingStart = bTime;
          }
        }
      });
      const deadlineDate = new Date(deadlineStr);
      if (earliestBookingStart && deadlineDate <= earliestBookingStart) {
        elements.errCreateDeadline.textContent = `Must arrive by time (${formatDateTime(deadlineDate.toISOString())}) must be strictly after the earliest booking departure (${formatDateTime(earliestBookingStart.toISOString())})`;
        return false;
      }
      elements.errCreateDeadline.textContent = '';
      return true;
    }

    return true;
  }

  function validateAllCreateFields() {
    createFormState.hasAttemptedSubmit = true;

    const isTitleValid = validateCreateField('title');
    const isPhoneValid = validateCreateField('phone');
    const isEmailValid = validateCreateField('email');
    const isBudgetValid = validateCreateField('budget');
    const isDeadlineValid = validateCreateField('deadline');

    const bookings = collectBookingsFromBuilder();
    let isBookingsValid = true;

    // Requirement: Require at least one booking before saving, with a clear error message
    if (bookings.length === 0) {
      elements.errCreateBookings.textContent = 'At least one booking is required before saving a trip. Add a booking manually or extract from confirmation text.';
      isBookingsValid = false;
    } else {
      // Validate that each booking has provider, origin, destination
      let missingFields = false;
      bookings.forEach((b) => {
        if (!b.provider || !b.from || !b.to || !b.startTime || !b.endTime) {
          missingFields = true;
        }
      });
      if (missingFields) {
        elements.errCreateBookings.textContent = 'Please fill in required fields (provider, origin, destination, start and end times) for all bookings.';
        isBookingsValid = false;
      } else {
        elements.errCreateBookings.textContent = '';
      }
    }

    // Validate cab requirement if checked
    if (elements.createTripNeedsCab && elements.createTripNeedsCab.checked) {
      const dropoff = elements.createTripCabDropoff ? elements.createTripCabDropoff.value.trim() : '';
      if (!dropoff) {
        if (elements.errCreateCabDropoff) elements.errCreateCabDropoff.textContent = 'Drop-off location is required when cab is requested';
        return false;
      } else {
        if (elements.errCreateCabDropoff) elements.errCreateCabDropoff.textContent = '';
      }
    }

    // Validate hotel requirement if checked
    if (elements.createTripNeedsHotel && elements.createTripNeedsHotel.checked) {
      const hName = elements.createTripHotelName ? elements.createTripHotelName.value.trim() : '';
      const hAddr = elements.createTripHotelAddress ? elements.createTripHotelAddress.value.trim() : '';
      let hotelValid = true;
      if (!hName) {
        if (elements.errCreateHotelName) elements.errCreateHotelName.textContent = 'Hotel name is required';
        hotelValid = false;
      } else {
        if (elements.errCreateHotelName) elements.errCreateHotelName.textContent = '';
      }
      if (!hAddr) {
        if (elements.errCreateHotelAddress) elements.errCreateHotelAddress.textContent = 'Hotel address is required';
        hotelValid = false;
      } else {
        if (elements.errCreateHotelAddress) elements.errCreateHotelAddress.textContent = '';
      }
      if (!hotelValid) return false;
    }

    return isTitleValid && isPhoneValid && isEmailValid && isBudgetValid && isDeadlineValid && isBookingsValid;
  }

  function openCreateTripModal() {
    elements.formCreateTrip.reset();
    createFormState.hasAttemptedSubmit = false;
    createFormState.touchedFields.clear();
    createFormState.rowCounter = 0;

    // Clear all validation errors on fresh form
    elements.errCreateTitle.textContent = '';
    elements.errCreatePhone.textContent = '';
    if (elements.errCreateEmail) elements.errCreateEmail.textContent = '';
    elements.errCreateBudget.textContent = '';
    elements.errCreateDeadline.textContent = '';
    elements.errCreateBookings.textContent = '';
    if (elements.errCreateCabDropoff) elements.errCreateCabDropoff.textContent = '';
    if (elements.errCreateHotelName) elements.errCreateHotelName.textContent = '';
    if (elements.errCreateHotelAddress) elements.errCreateHotelAddress.textContent = '';

    // Clear commitments & builder bookings
    elements.createCommitmentsList.innerHTML = '';
    elements.createCommitmentsEmpty.classList.remove('hidden');
    elements.createBookingsBuilderList.innerHTML = '';
    elements.createBuilderCount.textContent = '0';
    elements.createBuilderEmpty.classList.remove('hidden');

    if (elements.createParserUnparsed) {
      elements.createParserUnparsed.innerHTML = '';
      elements.createParserUnparsed.classList.add('hidden');
    }

    // Reset cab & hotel options
    if (elements.createTripNeedsCab) elements.createTripNeedsCab.checked = false;
    if (elements.createCabFields) elements.createCabFields.classList.add('hidden');
    if (elements.createTripCabDropoff) elements.createTripCabDropoff.value = '';
    if (elements.createTripCabProvider) elements.createTripCabProvider.value = '';

    if (elements.createTripNeedsHotel) elements.createTripNeedsHotel.checked = false;
    if (elements.createHotelFields) elements.createHotelFields.classList.add('hidden');
    if (elements.createTripHotelName) elements.createTripHotelName.value = '';
    if (elements.createTripHotelAddress) elements.createTripHotelAddress.value = '';
    if (elements.createTripHotelCheckin) elements.createTripHotelCheckin.value = '';
    if (elements.createTripHotelCheckout) elements.createTripHotelCheckout.value = '';
    if (elements.createTripHotelCost) elements.createTripHotelCost.value = '';
    if (elements.createTripHotelEmail) elements.createTripHotelEmail.value = '';

    // Notification defaults
    if (elements.createTripNotifyEmail) elements.createTripNotifyEmail.checked = true;
    if (elements.createTripNotifySms) elements.createTripNotifySms.checked = true;
    if (elements.createTripNotifyWhatsapp) elements.createTripNotifyWhatsapp.checked = false;

    // Sensible defaults
    elements.createTripBudget.value = '15000';
    elements.createTripAvoidOvernight.checked = true;

    const defaultDeadline = new Date();
    defaultDeadline.setDate(defaultDeadline.getDate() + 1);
    defaultDeadline.setHours(18, 0, 0, 0);
    elements.createTripDeadline.value = defaultDeadline.toISOString().substring(0, 16);

    // Phase 9: Pre-fill traveller defaults from logged in user profile
    if (state.currentUser) {
      if (elements.createTripTraveller && state.currentUser.name) {
        elements.createTripTraveller.value = state.currentUser.name;
      }
      if (elements.createTripPhone && state.currentUser.phone) {
        elements.createTripPhone.value = state.currentUser.phone;
      }
      if (elements.createTripEmail && state.currentUser.email) {
        elements.createTripEmail.value = state.currentUser.email;
      }
    }

    elements.modalCreateTrip.classList.remove('hidden');
  }

  function clientParseBookingLine(line) {
    const trimmed = line.trim();
    if (!trimmed) return null;

    let type = null;
    const lower = trimmed.toLowerCase();
    if (lower.includes('flight') || lower.includes('airline') || lower.includes('indigo') || lower.includes('air india') || lower.includes('vistara')) type = 'FLIGHT';
    else if (lower.includes('train') || lower.includes('express') || lower.includes('railway') || lower.includes('vande bharat') || lower.includes('shatabdi')) type = 'TRAIN';
    else if (lower.includes('bus') || lower.includes('ksrtc') || lower.includes('sarige') || lower.includes('sleeper') || lower.includes('volvo')) type = 'BUS';
    else if (lower.includes('cab') || lower.includes('taxi') || lower.includes('ola') || lower.includes('uber')) type = 'CAB';
    else if (lower.includes('hotel') || lower.includes('resort') || lower.includes('inn') || lower.includes('stay') || lower.includes('check-in') || lower.includes('check in')) type = 'HOTEL';
    else if (lower.includes('departs') && lower.includes('arrives')) type = 'BUS';
    else return null;    // Date: ISO or 21 Oct 2026
    let dateStr = '2026-10-21';
    const isoDateMatch = trimmed.match(/\b(\d{4}-\d{2}-\d{2})\b/);
    const textDateMatch = trimmed.match(/\b(\d{1,2})(?:st|nd|rd|th)?[\s\-]+([A-Za-z]{3,9})[\s\-]+(\d{4})\b/i);
    if (isoDateMatch) {
      dateStr = isoDateMatch[1];
    } else if (textDateMatch) {
      const d = String(textDateMatch[1]).padStart(2, '0');
      const mStr = textDateMatch[2].toLowerCase().substring(0, 3);
      const months = { jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06', jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12' };
      const m = months[mStr] || '10';
      const y = textDateMatch[3];
      dateStr = `${y}-${m}-${d}`;
    }

    let from = 'Origin';
    let to = 'Destination';
    let startTime = `${dateStr}T10:00:00`;
    let endTime = `${dateStr}T12:00:00`;

    // 1. "departs <city> 10:00 arrives <city> 14:30"
    const depArrMatch = trimmed.match(/departs\s+([A-Za-z\s()]+?)\s+(?:(?:on|at|\d{4}-\d{2}-\d{2}|\d{1,2}[\s\-]+[A-Za-z]{3,9}[\s\-]+\d{4})\s+)*(\d{1,2}:\d{2})\s+arrives\s+([A-Za-z\s()]+?)\s+(?:(?:on|at|\d{4}-\d{2}-\d{2}|\d{1,2}[\s\-]+[A-Za-z]{3,9}[\s\-]+\d{4})\s+)*(\d{1,2}:\d{2})/i);
    if (depArrMatch) {
      from = depArrMatch[1].trim();
      const t1 = depArrMatch[2].length === 4 ? '0' + depArrMatch[2] : depArrMatch[2];
      to = depArrMatch[3].trim();
      const t2 = depArrMatch[4].length === 4 ? '0' + depArrMatch[4] : depArrMatch[4];
      startTime = `${dateStr}T${t1}:00`;
      endTime = `${dateStr}T${t2}:00`;
    } else {
      const fromToMatch = trimmed.match(/(?:from|origin)\s*[:\-]?\s*([A-Za-z0-9\-\s()]+?)\s+(?:to|destination)\s*[:\-]?\s*([A-Za-z0-9\-\s()]+?)(?=\s+(?:departs|arrives|check|cost|fare)|\n|$|,)/i);
      if (fromToMatch) {
        from = fromToMatch[1].trim();
        to = fromToMatch[2].trim();
      }

      const checkInMatch = trimmed.match(/(?:check-?in)\s*[:\-]?\s*(?:(?:on|at|\d{4}-\d{2}-\d{2}|\d{1,2}[\s\-]+[A-Za-z]{3,9}[\s\-]+\d{4})\s+)*(\d{1,2}:\d{2})/i);
      if (checkInMatch) {
        const t = checkInMatch[1].length === 4 ? '0' + checkInMatch[1] : checkInMatch[1];
        startTime = `${dateStr}T${t}:00`;
        endTime = `${dateStr}T18:00:00`;
        if (lower.includes('goa')) { from = 'Goa'; to = 'Taj Resort Goa'; }
        else if (lower.includes('mangaluru')) { from = 'Mangaluru'; to = 'The Gateway Hotel'; }
      } else {
        const times = trimmed.match(/\b([0-2]?[0-9]:[0-5][0-9])\b/g);
        if (times && times.length >= 2) {
          startTime = `${dateStr}T${times[0].length === 4 ? '0' + times[0] : times[0]}:00`;
          endTime = `${dateStr}T${times[1].length === 4 ? '0' + times[1] : times[1]}:00`;
        } else if (times && times.length === 1) {
          startTime = `${dateStr}T${times[0].length === 4 ? '0' + times[0] : times[0]}:00`;
          endTime = `${dateStr}T12:00:00`;
        }
      }
    }

    // Provider
    let provider = type + ' Service';
    const prefixMatch = trimmed.match(/(?:bus|cab|train|flight|hotel)\s+(?:confirmation|reservation|booking|ticket)?\s*[:\-]?\s*([A-Za-z0-9\-\s()]+?)(?=\s+(?:departs|from|to|check|at|cost|fare)|\n|$|,)/i);
    if (prefixMatch && prefixMatch[1].trim()) {
      provider = prefixMatch[1].trim();
    } else if (lower.includes('ksrtc sarige')) provider = 'KSRTC Sarige';
    else if (lower.includes('ola')) provider = 'Ola';
    else if (lower.includes('taj resort goa')) provider = 'Taj Resort Goa';
    else if (lower.includes('gateway hotel')) provider = 'The Gateway Hotel';

    // Cost: "Rs 650", "INR 650", etc.
    let cost = 0;
    const costMatch = trimmed.match(/(?:rs\.?|inr|₹|cost\s*[:\-]?|fare\s*[:\-]?)\s*[:\-]?\s*(\d+(?:\.\d{1,2})?)/i);
    if (costMatch) {
      cost = parseFloat(costMatch[1]);
    }

    return {
      type,
      provider,
      from,
      to,
      startTime,
      endTime,
      cost,
      status: 'CONFIRMED',
    };
  }

  async function handleParseEmailText() {
    const text = elements.createEmailText.value.trim();
    if (!text) {
      showToast('Please paste confirmation email text first', 'warning');
      return;
    }

    setButtonLoading(elements.btnParseEmailCreate, true);
    if (elements.createParserUnparsed) {
      elements.createParserUnparsed.innerHTML = '';
      elements.createParserUnparsed.classList.add('hidden');
    }

    try {
      let extracted = [];
      let unparsed = [];

      // Try server parse first
      try {
        const res = await fetch('/api/trips/parse', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ text: text }),
        });
        const data = await res.json();
        if (res.ok && data.success && data.data) {
          const parseResult = data.data;
          extracted = Array.isArray(parseResult) ? parseResult : (parseResult.bookings || []);
          unparsed = parseResult.unparsedLines || [];
        }
      } catch (apiErr) {
        // Fall back to client parser
      }

      // If server returned "Unknown" for route or empty unparsed lines, enhance with client parser
      const lines = text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.length > 0);
      const clientBookings = [];
      const clientUnparsed = [];

      lines.forEach((line) => {
        const parsed = clientParseBookingLine(line);
        if (parsed) {
          clientBookings.push(parsed);
        } else {
          clientUnparsed.push('Could not read this line: ' + line);
        }
      });

      // Use richer parse output
      if (clientBookings.length > 0 && (extracted.length === 0 || extracted.some((b) => b.from === 'Unknown'))) {
        extracted = clientBookings;
        unparsed = clientUnparsed;
      } else if (unparsed.length === 0 && clientUnparsed.length > 0) {
        unparsed = clientUnparsed;
      }

      // Display unparsed line warnings (Requirement 3: Never silently skip it)
      if (unparsed.length > 0 && elements.createParserUnparsed) {
        elements.createParserUnparsed.classList.remove('hidden');
        unparsed.forEach((msg) => {
          const alert = document.createElement('div');
          alert.className = 'unparsed-line-alert';
          alert.textContent = msg;
          elements.createParserUnparsed.appendChild(alert);
        });
      }

      if (extracted.length === 0) {
        showToast('No structured bookings could be recognized from the text', 'warning');
        return;
      }

      // Add each extracted booking directly into the editable manual builder
      extracted.forEach((b) => {
        addBookingBuilderRow(b);
      });

      showToast(`Extracted ${extracted.length} bookings into editable itinerary builder`, 'success');
      elements.errCreateBookings.textContent = '';

      // Re-validate deadline if touched
      if (createFormState.touchedFields.has('deadline') || createFormState.hasAttemptedSubmit) {
        validateCreateField('deadline');
      }
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnParseEmailCreate, false);
    }
  }

  async function handleCreateTripSubmit(e) {
    e.preventDefault();

    const isValid = validateAllCreateFields();
    if (!isValid) {
      showToast('Please correct the validation errors in the form before charting expedition.', 'warning');
      return;
    }

    const title = elements.createTripTitle.value.trim();
    const budgetVal = parseFloat(elements.createTripBudget.value);
    const rawPhone = elements.createTripPhone.value.trim();
    const rawEmail = elements.createTripEmail ? elements.createTripEmail.value.trim() : '';

    // Phone normalization (accept 10 digits, store as +91XXXXXXXXXX)
    let cleanPhone = rawPhone.replace(/\D/g, '');
    if (cleanPhone.length === 12 && cleanPhone.startsWith('91')) {
      cleanPhone = cleanPhone.substring(2);
    } else if (cleanPhone.length === 11 && cleanPhone.startsWith('0')) {
      cleanPhone = cleanPhone.substring(1);
    }
    const normalizedPhone = '+91' + cleanPhone;

    const deadlineStr = elements.createTripDeadline.value;
    const selectedBookings = collectBookingsFromBuilder();

    // Structured hard commitments
    const structuredCommitments = [];
    elements.createCommitmentsList.querySelectorAll('.commitment-row').forEach((row) => {
      const name = row.querySelector('.commitment-name').value.trim();
      const time = row.querySelector('.commitment-time').value;
      const loc = row.querySelector('.commitment-location').value.trim();
      if (name && time) {
        structuredCommitments.push({
          name: name,
          commitmentTime: time.length === 16 ? time + ':00' : time,
          location: loc,
        });
      }
    });

    const travellerName = elements.createTripTraveller.value.trim() || 'Traveller';
    const description = elements.createTripDesc.value.trim();
    const avoidOvernight = elements.createTripAvoidOvernight.checked;

    // Optional Cab & Hotel preferences
    const needsCab = elements.createTripNeedsCab ? elements.createTripNeedsCab.checked : false;
    const cabDropoff = (needsCab && elements.createTripCabDropoff) ? elements.createTripCabDropoff.value.trim() : null;
    const needsHotel = elements.createTripNeedsHotel ? elements.createTripNeedsHotel.checked : false;

    if (needsHotel) {
      const hotelName = elements.createTripHotelName ? elements.createTripHotelName.value.trim() : '';
      const hotelAddress = elements.createTripHotelAddress ? elements.createTripHotelAddress.value.trim() : '';
      const hotelCheckin = elements.createTripHotelCheckin ? elements.createTripHotelCheckin.value : '';
      const hotelCheckout = elements.createTripHotelCheckout ? elements.createTripHotelCheckout.value : '';
      const hotelCost = elements.createTripHotelCost ? (parseFloat(elements.createTripHotelCost.value) || 0) : 0;

      if (hotelName) {
        selectedBookings.push({
          id: 'HOTEL-' + Date.now(),
          type: 'HOTEL',
          provider: hotelName,
          from: hotelAddress || 'Destination',
          to: hotelAddress || 'Destination',
          startTime: hotelCheckin ? (hotelCheckin.length === 16 ? hotelCheckin + ':00' : hotelCheckin) : null,
          endTime: hotelCheckout ? (hotelCheckout.length === 16 ? hotelCheckout + ':00' : hotelCheckout) : null,
          cost: hotelCost,
          dependsOn: [],
          status: 'CONFIRMED',
          hotelContactEmail: elements.createTripHotelEmail ? elements.createTripHotelEmail.value.trim() : null,
        });
      }
    }

    const payload = {
      title: title,
      description: description,
      travellerName: travellerName,
      travellerPhone: normalizedPhone,
      travellerEmail: rawEmail || null,
      needsDestinationCab: needsCab,
      destinationCabDropoff: cabDropoff,
      needsHotel: needsHotel,
      notifyEmail: elements.createTripNotifyEmail ? elements.createTripNotifyEmail.checked : true,
      notifySms: elements.createTripNotifySms ? elements.createTripNotifySms.checked : true,
      notifyWhatsapp: elements.createTripNotifyWhatsapp ? elements.createTripNotifyWhatsapp.checked : false,
      constraints: {
        maxBudget: budgetVal,
        mustArriveBy: deadlineStr ? (deadlineStr.length === 16 ? deadlineStr + ':00' : deadlineStr) : null,
        avoidOvernightTravel: avoidOvernight,
        preferredModes: ['FLIGHT', 'CAB', 'TRAIN', 'BUS'],
        hardCommitments: structuredCommitments.map((c) => c.name),
        structuredCommitments: structuredCommitments,
      },
      hardCommitments: structuredCommitments,
      bookings: selectedBookings,
    };

    setButtonLoading(document.getElementById('btn-submit-create'), true);

    try {
      const res = await fetch('/api/trips', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to create trip');
      }

      const createdTrip = data.data;
      elements.modalCreateTrip.classList.add('hidden');
      showToast(`Expedition "${createdTrip.title}" charted successfully!`, 'success');

      // Requirement 5: After saving, open the new trip's workspace automatically and show it in the trips list
      await fetchTripsDirectory();
      await loadTripById(createdTrip.id);
      switchView('workspace');
    } catch (err) {
      showToast(err.message, 'danger');
      elements.errCreateBookings.textContent = err.message;
    } finally {
      setButtonLoading(document.getElementById('btn-submit-create'), false);
    }
  }

  // ==========================================
  // Demo Reset & Load Demo Trip
  // ==========================================

  async function handleLoadDemo() {
    setButtonLoading(elements.btnLoadDemo, true);
    try {
      await loadTripById('TRIP-DEMO-001');
    } finally {
      setButtonLoading(elements.btnLoadDemo, false);
    }
  }

  async function handleResetDemo() {
    setButtonLoading(elements.btnReset, true);
    try {
      const res = await fetch('/api/demo/reset', { method: 'POST' });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to reset demo trip');
      }
      showToast('Demo trip TRIP-DEMO-001 reset to pristine initial state.', 'info');
      await loadTripById('TRIP-DEMO-001');
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnReset, false);
    }
  }

  // ==========================================
  // Edit Trip Preferences Modal
  // ==========================================

  function openEditTripModal() {
    if (!state.trip) return;
    const t = state.trip;
    elements.editTripTitle.value = t.title || '';
    elements.editTripTraveller.value = t.travellerName || '';
    elements.editTripPhone.value = t.travellerPhone || '';
    elements.editTripEmail.value = t.travellerEmail || '';
    elements.editTripBudget.value = t.constraints ? t.constraints.maxBudget : '';
    elements.editTripDeadline.value = t.constraints && t.constraints.mustArriveBy ? t.constraints.mustArriveBy.substring(0, 16) : '';
    elements.editTripDesc.value = t.description || '';
    elements.editTripAvoidOvernight.checked = t.constraints ? t.constraints.avoidOvernightTravel : true;
    if (elements.editTripNotifyEmail) elements.editTripNotifyEmail.checked = t.notifyEmail !== false;
    if (elements.editTripNotifySms) elements.editTripNotifySms.checked = t.notifySms !== false;
    if (elements.editTripNotifyWhatsapp) elements.editTripNotifyWhatsapp.checked = !!t.notifyWhatsapp;

    const needsCab = !!t.needsDestinationCab;
    elements.editTripNeedsCab.checked = needsCab;
    if (elements.editCabFields) {
      elements.editCabFields.classList.toggle('hidden', !needsCab);
    }
    if (elements.editTripCabDropoff) {
      elements.editTripCabDropoff.value = t.destinationCabDropoff || '';
    }
    if (elements.errEditTitle) elements.errEditTitle.textContent = '';
    if (elements.errEditPhone) elements.errEditPhone.textContent = '';
    if (elements.errEditEmail) elements.errEditEmail.textContent = '';
    if (elements.errEditBudget) elements.errEditBudget.textContent = '';
    if (elements.errEditDeadline) elements.errEditDeadline.textContent = '';
    if (elements.errEditCabDropoff) elements.errEditCabDropoff.textContent = '';

    elements.modalEditTrip.classList.remove('hidden');
  }

  async function handleSaveEditTrip(e) {
    e.preventDefault();
    if (!state.trip) return;
    const tripId = state.trip.id;

    const title = elements.editTripTitle.value.trim();
    if (!title) {
      if (elements.errEditTitle) elements.errEditTitle.textContent = 'Trip title cannot be blank';
      return;
    }

    const rawPhone = elements.editTripPhone.value.trim();
    let cleanPhone = rawPhone.replace(/\D/g, '');
    if (cleanPhone.length === 12 && cleanPhone.startsWith('91')) cleanPhone = cleanPhone.substring(2);
    else if (cleanPhone.length === 11 && cleanPhone.startsWith('0')) cleanPhone = cleanPhone.substring(1);
    if (cleanPhone.length !== 10) {
      if (elements.errEditPhone) elements.errEditPhone.textContent = 'Phone number must be exactly 10 digits';
      return;
    }
    const normalizedPhone = '+91' + cleanPhone;

    const rawEmail = elements.editTripEmail ? elements.editTripEmail.value.trim() : '';
    if (rawEmail) {
      const emailRegex = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
      if (!emailRegex.test(rawEmail)) {
        if (elements.errEditEmail) elements.errEditEmail.textContent = 'Invalid email format';
        return;
      }
    }

    const budgetVal = parseFloat(elements.editTripBudget.value);
    if (!budgetVal || budgetVal <= 0) {
      if (elements.errEditBudget) elements.errEditBudget.textContent = 'Budget must be positive';
      return;
    }

    const deadlineStr = elements.editTripDeadline.value;
    if (!deadlineStr) {
      if (elements.errEditDeadline) elements.errEditDeadline.textContent = 'Deadline is required';
      return;
    }

    const needsCab = elements.editTripNeedsCab.checked;
    const cabDropoff = elements.editTripCabDropoff ? elements.editTripCabDropoff.value.trim() : '';
    if (needsCab && !cabDropoff) {
      if (elements.errEditCabDropoff) elements.errEditCabDropoff.textContent = 'Drop-off location is required when cab is requested';
      return;
    }

    const payload = {
      ...state.trip,
      title: title,
      travellerName: elements.editTripTraveller.value.trim() || 'Traveller',
      travellerPhone: normalizedPhone,
      travellerEmail: rawEmail || null,
      description: elements.editTripDesc.value.trim(),
      needsDestinationCab: needsCab,
      destinationCabDropoff: needsCab ? cabDropoff : null,
      notifyEmail: elements.editTripNotifyEmail ? elements.editTripNotifyEmail.checked : true,
      notifySms: elements.editTripNotifySms ? elements.editTripNotifySms.checked : true,
      notifyWhatsapp: elements.editTripNotifyWhatsapp ? elements.editTripNotifyWhatsapp.checked : false,
      constraints: {
        ...(state.trip.constraints || {}),
        maxBudget: budgetVal,
        mustArriveBy: deadlineStr ? (deadlineStr.length === 16 ? deadlineStr + ':00' : deadlineStr) : null,
        avoidOvernightTravel: elements.editTripAvoidOvernight.checked,
      },
    };

    setButtonLoading(elements.btnSaveEditTrip, true);
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to update trip');
      }
      elements.modalEditTrip.classList.add('hidden');
      showToast('Trip preferences updated successfully!', 'success');
      await loadTripById(tripId);
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnSaveEditTrip, false);
    }
  }

  // ==========================================
  // Event Listeners Registration
  // ==========================================

  function initEventListeners() {
    console.log("Registering event listeners (auth listeners first)");

    // Phase 9: Authentication & Account Listeners (Bound FIRST at the very top)
    const btnOpenLogin = elements.btnOpenLogin || document.getElementById('btn-open-login');
    if (btnOpenLogin) {
      btnOpenLogin.addEventListener('click', () => {
        console.log("Sign In clicked");
        openLoginModal();
      });
    }

    const btnPanelLogin = elements.btnPanelLogin || document.getElementById('btn-panel-login');
    if (btnPanelLogin) {
      btnPanelLogin.addEventListener('click', () => {
        console.log("Sign In clicked");
        openLoginModal();
      });
    }

    const btnCloseLogin = elements.btnCloseLoginModal || document.getElementById('btn-close-login-modal');
    if (btnCloseLogin) btnCloseLogin.addEventListener('click', closeLoginModal);

    const formLogin = elements.formLogin || document.getElementById('form-login');
    if (formLogin) formLogin.addEventListener('submit', handleLogin);

    const linkGotoRegister = elements.linkGotoRegister || document.getElementById('link-goto-register');
    if (linkGotoRegister) linkGotoRegister.addEventListener('click', () => {
      closeLoginModal();
      openRegisterModal();
    });

    const btnOpenRegister = elements.btnOpenRegister || document.getElementById('btn-open-register');
    if (btnOpenRegister) {
      btnOpenRegister.addEventListener('click', () => {
        console.log("Register clicked");
        openRegisterModal();
      });
    }

    const btnPanelRegister = elements.btnPanelRegister || document.getElementById('btn-panel-register');
    if (btnPanelRegister) {
      btnPanelRegister.addEventListener('click', () => {
        console.log("Register clicked");
        openRegisterModal();
      });
    }

    const btnCloseRegister = elements.btnCloseRegisterModal || document.getElementById('btn-close-register-modal');
    if (btnCloseRegister) btnCloseRegister.addEventListener('click', closeRegisterModal);

    const formRegister = elements.formRegister || document.getElementById('form-register');
    if (formRegister) formRegister.addEventListener('submit', handleRegister);

    const linkGotoLogin = elements.linkGotoLogin || document.getElementById('link-goto-login');
    if (linkGotoLogin) linkGotoLogin.addEventListener('click', () => {
      closeRegisterModal();
      openLoginModal();
    });

    const userProfilePill = elements.userProfilePill || document.getElementById('user-profile-pill');
    if (userProfilePill) userProfilePill.addEventListener('click', openAccountModal);

    const btnCloseAccount = elements.btnCloseAccountModal || document.getElementById('btn-close-account-modal');
    if (btnCloseAccount) btnCloseAccount.addEventListener('click', closeAccountModal);

    const formAccountProfile = elements.formAccountProfile || document.getElementById('form-account-profile');
    if (formAccountProfile) formAccountProfile.addEventListener('submit', handleSaveProfile);

    const formAccountPassword = elements.formAccountPassword || document.getElementById('form-account-password');
    if (formAccountPassword) formAccountPassword.addEventListener('submit', handleChangePassword);

    const btnLogout = elements.btnLogout || document.getElementById('btn-logout');
    if (btnLogout) btnLogout.addEventListener('click', handleLogout);

    // Close modals when clicking on background overlay
    ['modal-login', 'modal-register', 'modal-account', 'modal-confirm-booking'].forEach((id) => {
      const modal = document.getElementById(id);
      if (modal) {
        modal.addEventListener('click', (e) => {
          if (e.target === modal) {
            modal.classList.add('hidden');
            modal.classList.remove('is-open');
          }
        });
      }
    });

    // Top Navigation Tabs
    elements.navBtnDirectory.addEventListener('click', () => switchView('directory'));
    elements.navBtnWorkspace.addEventListener('click', () => switchView('workspace'));
    elements.btnBackToDirectory.addEventListener('click', () => switchView('directory'));

    // Global Action Buttons
    elements.btnLoadDemo.addEventListener('click', handleLoadDemo);
    elements.btnReset.addEventListener('click', handleResetDemo);
    elements.btnRefreshTrips.addEventListener('click', fetchTripsDirectory);
    elements.btnOpenCreateTrip.addEventListener('click', openCreateTripModal);
    elements.btnDirNewTrip.addEventListener('click', openCreateTripModal);
    if (elements.btnEditTrip) elements.btnEditTrip.addEventListener('click', openEditTripModal);

    // Edit Trip Modal Events
    if (elements.btnCloseEditModal) elements.btnCloseEditModal.addEventListener('click', () => elements.modalEditTrip.classList.add('hidden'));
    if (elements.btnCancelEditTrip) elements.btnCancelEditTrip.addEventListener('click', () => elements.modalEditTrip.classList.add('hidden'));
    if (elements.formEditTrip) elements.formEditTrip.addEventListener('submit', handleSaveEditTrip);
    if (elements.editTripNeedsCab) {
      elements.editTripNeedsCab.addEventListener('change', (e) => {
        if (elements.editCabFields) elements.editCabFields.classList.toggle('hidden', !e.target.checked);
      });
    }

    // Timeline Add Booking
    elements.btnAddBooking.addEventListener('click', () => openBookingEditor(null));

    // Create Trip Modal Events
    elements.btnCloseCreateModal.addEventListener('click', () => elements.modalCreateTrip.classList.add('hidden'));
    elements.btnCancelCreate.addEventListener('click', () => elements.modalCreateTrip.classList.add('hidden'));
    elements.btnParseEmailCreate.addEventListener('click', handleParseEmailText);
    if (elements.createTripNeedsCab) {
      elements.createTripNeedsCab.addEventListener('change', (e) => {
        if (elements.createCabFields) elements.createCabFields.classList.toggle('hidden', !e.target.checked);
      });
    }
    if (elements.createTripNeedsHotel) {
      elements.createTripNeedsHotel.addEventListener('change', (e) => {
        if (elements.createHotelFields) elements.createHotelFields.classList.toggle('hidden', !e.target.checked);
      });
    }
    if (elements.btnAddBookingRow) {
      elements.btnAddBookingRow.addEventListener('click', () => addBookingBuilderRow());
    }
    if (elements.btnAddCommitment) {
      elements.btnAddCommitment.addEventListener('click', () => addCommitmentRow());
    }
    elements.formCreateTrip.addEventListener('submit', handleCreateTripSubmit);

    // Validation only on touch (blur) or after submit attempt
    const setupFieldValidation = (elem, fieldName) => {
      if (!elem) return;
      elem.addEventListener('blur', () => {
        createFormState.touchedFields.add(fieldName);
        validateCreateField(fieldName);
      });
      elem.addEventListener('input', () => {
        if (createFormState.touchedFields.has(fieldName) || createFormState.hasAttemptedSubmit) {
          validateCreateField(fieldName);
        }
      });
    };

    setupFieldValidation(elements.createTripTitle, 'title');
    setupFieldValidation(elements.createTripPhone, 'phone');
    setupFieldValidation(elements.createTripEmail, 'email');
    setupFieldValidation(elements.createTripBudget, 'budget');
    setupFieldValidation(elements.createTripDeadline, 'deadline');

    // Booking Editor Modal Events
    elements.btnCloseBookingModal.addEventListener('click', () => elements.modalBookingEditor.classList.add('hidden'));
    elements.btnCancelBooking.addEventListener('click', () => elements.modalBookingEditor.classList.add('hidden'));
    elements.formBookingEditor.addEventListener('submit', handleSaveBooking);

    // Disruption Type Switcher
    elements.selectDisruptionType.addEventListener('change', (e) => {
      const isDelay = e.target.value === 'FLIGHT_DELAY';
      elements.groupDelayMinutes.classList.toggle('hidden', !isDelay);
    });

    elements.btnInjectDisruption.addEventListener('click', handleInjectDisruption);
    elements.btnFindRecovery.addEventListener('click', () => handleFindRecovery(null));

    // Constraint Relaxation Buttons
    if (elements.btnRelaxOvernight) {
      elements.btnRelaxOvernight.addEventListener('click', () => handleRelaxConstraint('overnight'));
    }
    if (elements.btnRelaxDeadline) {
      elements.btnRelaxDeadline.addEventListener('click', () => handleRelaxConstraint('deadline'));
    }
    if (elements.btnRelaxBudget) {
      elements.btnRelaxBudget.addEventListener('click', () => handleRelaxConstraint('budget'));
    }
    if (elements.btnRelaxReset) {
      elements.btnRelaxReset.addEventListener('click', () => handleRelaxConstraint('reset'));
    }

    // Rejected Options Accordion Toggle
    elements.btnToggleRejected.addEventListener('click', () => {
      const isExpanded = elements.btnToggleRejected.getAttribute('aria-expanded') === 'true';
      elements.btnToggleRejected.setAttribute('aria-expanded', String(!isExpanded));
      elements.rejectedOptionsContent.classList.toggle('hidden', isExpanded);
      const arrow = elements.btnToggleRejected.querySelector('.accordion-arrow');
      if (arrow) {
        arrow.innerHTML = isExpanded ? '&#9662;' : '&#9652;';
      }
    });

    // Plan Approval & Rejection
    elements.btnApprovePlan.addEventListener('click', handleApprovePlan);
    elements.btnRejectPlan.addEventListener('click', handleRejectPlan);

    // Option Card Selection (delegated once on container)
    elements.optionsCardsContainer.addEventListener('click', (e) => {
      const btn = e.target.closest('.btn-select-option');
      if (!btn || btn.disabled) return;
      const card = btn.closest('.option-card');
      const optId = card ? card.getAttribute('data-option-id') : btn.getAttribute('data-option-id');
      if (optId) {
        handleSelectOption(optId, btn);
      }
    });

    // Copy Hotel Message Button
    elements.btnCopyHotelMessage.addEventListener('click', async () => {
      const text = elements.hotelMessageBox.textContent;
      try {
        await navigator.clipboard.writeText(text);
        showToast('Hotel message copied to clipboard!', 'success');
      } catch (e) {
        // Fallback for non-secure contexts
        const textarea = document.createElement('textarea');
        textarea.value = text;
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
        showToast('Hotel message copied to clipboard!', 'success');
      }
    });

    // Phase 8: Notifications events
    if (elements.btnRefreshNotifications) {
      elements.btnRefreshNotifications.addEventListener('click', () => {
        if (state.currentTripId) {
          loadChannelStatus();
          loadNotificationHistory(state.currentTripId);
          showToast('Refreshed notifications history', 'info');
        }
      });
    }
    if (elements.btnSendTestAlert) {
      elements.btnSendTestAlert.addEventListener('click', handleSendTestAlert);
    }
    if (elements.btnSendHotelEmail) {
      elements.btnSendHotelEmail.addEventListener('click', handleSendHotelEmail);
    }

    // Phase 7: Live Status & Simulation Events
    if (elements.btnCheckStatusNow) {
      elements.btnCheckStatusNow.addEventListener('click', handleCheckStatusNow);
    }
    if (elements.btnApplySimulation) {
      elements.btnApplySimulation.addEventListener('click', handleApplySimulation);
    }
    if (elements.selectSimulateState) {
      elements.selectSimulateState.addEventListener('change', (e) => {
        const isDelay = e.target.value === 'DELAYED';
        if (elements.groupSimulateDelay) {
          elements.groupSimulateDelay.classList.toggle('hidden', !isDelay);
        }
      });
    }
    if (elements.btnGotoRecovery) {
      elements.btnGotoRecovery.addEventListener('click', () => {
        const target = document.getElementById('section-recovery');
        if (target) {
          target.scrollIntoView({ behavior: 'smooth' });
        }
      });
    }

    // Report Problem Modal Events
    if (elements.btnCloseReportModal) {
      elements.btnCloseReportModal.addEventListener('click', closeReportProblemModal);
    }
    if (elements.btnCancelReportProblem) {
      elements.btnCancelReportProblem.addEventListener('click', closeReportProblemModal);
    }
    if (elements.formReportProblem) {
      elements.formReportProblem.addEventListener('submit', handleSubmitReportProblem);
    }
    if (elements.reportProblemType) {
      elements.reportProblemType.addEventListener('change', () => {
        const isCancelled = elements.reportProblemType.value === 'CANCELLED';
        if (elements.groupReportDelay) {
          elements.groupReportDelay.classList.toggle('hidden', isCancelled);
        }
      });
    }

    // Phase 9: Confirm Booking Modal Listeners
    if (elements.btnCloseConfirmModal) elements.btnCloseConfirmModal.addEventListener('click', closeConfirmBookingModal);
    if (elements.btnCancelConfirmBooking) elements.btnCancelConfirmBooking.addEventListener('click', closeConfirmBookingModal);
    if (elements.formConfirmBooking) elements.formConfirmBooking.addEventListener('submit', handleSubmitConfirmBooking);
  }

  // ==========================================
  // Phase 9: Auth & Handoff Service Handlers
  // ==========================================

  function setUnauthenticatedState(isUnauth) {
    const panel = elements.panelAuthRequired || document.getElementById('panel-auth-required');
    const dirList = elements.tripsDirectoryList || document.getElementById('trips-directory-list');
    const dirSummary = document.querySelector('.directory-summary-bar');

    if (isUnauth) {
      if (panel) panel.classList.remove('hidden');
      if (dirList) dirList.classList.add('hidden');
      if (dirSummary) dirSummary.classList.add('hidden');
      if (elements.btnOpenCreateTrip) elements.btnOpenCreateTrip.disabled = true;
      if (elements.btnLoadDemo) elements.btnLoadDemo.disabled = true;
      if (elements.btnReset) elements.btnReset.disabled = true;
      if (elements.btnDirNewTrip) elements.btnDirNewTrip.disabled = true;
      if (elements.btnRefreshTrips) elements.btnRefreshTrips.disabled = true;
    } else {
      if (panel) panel.classList.add('hidden');
      if (dirList) dirList.classList.remove('hidden');
      if (dirSummary) dirSummary.classList.remove('hidden');
      if (elements.btnOpenCreateTrip) elements.btnOpenCreateTrip.disabled = false;
      if (elements.btnLoadDemo) elements.btnLoadDemo.disabled = false;
      if (elements.btnReset) elements.btnReset.disabled = false;
      if (elements.btnDirNewTrip) elements.btnDirNewTrip.disabled = false;
      if (elements.btnRefreshTrips) elements.btnRefreshTrips.disabled = false;
    }
  }

  async function checkCurrentUser() {
    try {
      const res = await originalFetch('/api/auth/me', { credentials: 'same-origin' });
      if (res.ok) {
        const data = await res.json();
        if (data && data.success && data.data) {
          state.currentUser = data.data;
          updateAuthHeader();
          setUnauthenticatedState(false);
          fetchStatusMode();
          fetchTripsDirectory();
          loadTripById('TRIP-DEMO-001');
          return;
        }
      }
      state.currentUser = null;
      updateAuthHeader();
      setUnauthenticatedState(true);
    } catch (e) {
      state.currentUser = null;
      updateAuthHeader();
      setUnauthenticatedState(true);
    }
  }

  function updateAuthHeader() {
    if (state.currentUser) {
      if (elements.btnOpenLogin) elements.btnOpenLogin.classList.add('hidden');
      if (elements.btnOpenRegister) elements.btnOpenRegister.classList.add('hidden');
      if (elements.userProfilePill) {
        elements.userProfilePill.classList.remove('hidden');
        if (elements.userDisplayName) {
          elements.userDisplayName.textContent = state.currentUser.name || 'Account';
        }
      }
    } else {
      if (elements.btnOpenLogin) elements.btnOpenLogin.classList.remove('hidden');
      if (elements.btnOpenRegister) elements.btnOpenRegister.classList.remove('hidden');
      if (elements.userProfilePill) elements.userProfilePill.classList.add('hidden');
    }
  }

  function openLoginModal() {
    if (elements.errLogin) {
      elements.errLogin.textContent = '';
      elements.errLogin.classList.add('hidden');
    }
    if (elements.formLogin) elements.formLogin.reset();
    if (elements.modalLogin) {
      elements.modalLogin.classList.remove('hidden');
      elements.modalLogin.classList.add('is-open');
    }
    const emailInput = document.getElementById('login-email');
    if (emailInput) setTimeout(() => emailInput.focus(), 50);
  }

  function closeLoginModal() {
    if (elements.modalLogin) {
      elements.modalLogin.classList.add('hidden');
      elements.modalLogin.classList.remove('is-open');
    }
  }

  function openRegisterModal() {
    if (elements.errRegister) {
      elements.errRegister.textContent = '';
      elements.errRegister.classList.add('hidden');
    }
    if (elements.formRegister) elements.formRegister.reset();
    if (elements.modalRegister) {
      elements.modalRegister.classList.remove('hidden');
      elements.modalRegister.classList.add('is-open');
    }
    const nameInput = document.getElementById('reg-name');
    if (nameInput) setTimeout(() => nameInput.focus(), 50);
  }

  function closeRegisterModal() {
    if (elements.modalRegister) {
      elements.modalRegister.classList.add('hidden');
      elements.modalRegister.classList.remove('is-open');
    }
  }

  function openAccountModal() {
    if (!state.currentUser) {
      openLoginModal();
      return;
    }
    if (elements.accountEmail) elements.accountEmail.value = state.currentUser.email || '';
    if (elements.accountName) elements.accountName.value = state.currentUser.name || '';
    if (elements.accountPhone) elements.accountPhone.value = state.currentUser.phone || '';
    if (elements.errAccount) {
      elements.errAccount.textContent = '';
      elements.errAccount.classList.add('hidden');
    }
    if (elements.formAccountPassword) elements.formAccountPassword.reset();
    if (elements.modalAccount) {
      elements.modalAccount.classList.remove('hidden');
      elements.modalAccount.classList.add('is-open');
    }
  }

  function closeAccountModal() {
    if (elements.modalAccount) {
      elements.modalAccount.classList.add('hidden');
      elements.modalAccount.classList.remove('is-open');
    }
  }

  async function handleLogin(e) {
    e.preventDefault();
    const email = elements.loginEmail.value.trim();
    const password = elements.loginPassword.value;
    if (!email || !password) return;

    setButtonLoading(elements.btnSubmitLogin, true);
    if (elements.errLogin) {
      elements.errLogin.textContent = '';
      elements.errLogin.classList.add('hidden');
    }

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Invalid email or password');
      }

      state.currentUser = data.data;
      updateAuthHeader();
      setUnauthenticatedState(false);
      closeLoginModal();
      showToast('Welcome back, ' + (state.currentUser.name || 'traveller') + '!', 'success');

      fetchStatusMode();
      if (state.loginReturnTarget && state.loginReturnTarget.view === 'workspace' && state.loginReturnTarget.tripId) {
        const returnTripId = state.loginReturnTarget.tripId;
        state.loginReturnTarget = null;
        switchView('workspace');
        await loadTripById(returnTripId);
      } else {
        state.loginReturnTarget = null;
        switchView('directory');
        await fetchTripsDirectory();
      }
    } catch (err) {
      if (elements.errLogin) {
        elements.errLogin.textContent = err.message;
        elements.errLogin.classList.remove('hidden');
      }
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnSubmitLogin, false);
    }
  }

  async function handleRegister(e) {
    e.preventDefault();
    const name = elements.regName.value.trim();
    const email = elements.regEmail.value.trim();
    const password = elements.regPassword.value;
    const phone = elements.regPhone.value.trim();

    if (!name || !email || !password) return;

    setButtonLoading(elements.btnSubmitRegister, true);
    if (elements.errRegister) {
      elements.errRegister.textContent = '';
      elements.errRegister.classList.add('hidden');
    }

    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, password, phone }),
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Registration failed');
      }

      // Auto-login newly registered user
      try {
        const loginRes = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        });
        const loginData = await loginRes.json();
        if (loginRes.ok && loginData.success) {
          state.currentUser = loginData.data;
        } else {
          state.currentUser = data.data;
        }
      } catch (errLogin) {
        state.currentUser = data.data;
      }

      updateAuthHeader();
      setUnauthenticatedState(false);
      closeRegisterModal();
      showToast('Account created successfully! Welcome to Odyssey.', 'success');
      fetchStatusMode();
      switchView('directory');
      await fetchTripsDirectory();
    } catch (err) {
      if (elements.errRegister) {
        elements.errRegister.textContent = err.message;
        elements.errRegister.classList.remove('hidden');
      }
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnSubmitRegister, false);
    }
  }

  async function handleLogout() {
      setButtonLoading(elements.btnLogout, true);
      try {
        await fetch('/api/auth/logout', { method: 'POST' });
      } catch (e) {
        // ignore
      } finally {
        setButtonLoading(elements.btnLogout, false);
        state.currentUser = null;
        updateAuthHeader();
        closeAccountModal();
        setUnauthenticatedState(true);
        showToast('Signed out successfully.', 'info');
        switchView('directory');
      }
    }

    async function handleSaveProfile(e) {
      e.preventDefault();
      const name = elements.accountName.value.trim();
      const phone = elements.accountPhone.value.trim();
      if (!name) return;

      setButtonLoading(elements.btnSaveProfile, true);
      try {
        const res = await fetch('/api/auth/profile', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name, phone }),
        });
        const data = await res.json();
        if (!res.ok || !data.success) {
          throw new Error(data.message || 'Failed to update profile');
        }
        state.currentUser = data.data;
        updateAuthHeader();
        showToast('Profile updated successfully', 'success');
      } catch (err) {
        showToast(err.message, 'danger');
      } finally {
        setButtonLoading(elements.btnSaveProfile, false);
      }
    }

    async function handleChangePassword(e) {
      e.preventDefault();
      const currentPassword = elements.accountCurrPassword.value;
      const newPassword = elements.accountNewPassword.value;
      if (!currentPassword || !newPassword) return;

      setButtonLoading(elements.btnSubmitChangePassword, true);
      try {
        const res = await fetch('/api/auth/password', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ currentPassword, newPassword }),
        });
        const data = await res.json();
        if (!res.ok || !data.success) {
          throw new Error(data.message || 'Failed to update password');
        }
        elements.formAccountPassword.reset();
        showToast('Password changed successfully', 'success');
      } catch (err) {
        showToast(err.message, 'danger');
      } finally {
        setButtonLoading(elements.btnSubmitChangePassword, false);
      }
    }

    // Handoff & Booking Confirmation
    async function renderHandoffCard(trip) {
      if (!elements.sectionHandoffCard) return;
      const isResolved = trip && trip.status === 'RESOLVED';
      const isCompletedBookings = trip && trip.status === 'COMPLETED_BOOKINGS';

      if (!isResolved && !isCompletedBookings) {
        elements.sectionHandoffCard.classList.add('hidden');
        return;
      }

      elements.sectionHandoffCard.classList.remove('hidden');

      if (isCompletedBookings) {
        if (elements.handoffCompletedBanner) elements.handoffCompletedBanner.classList.remove('hidden');
        if (elements.handoffStatusBadge) {
          elements.handoffStatusBadge.textContent = 'ALL BOOKINGS CONFIRMED';
          elements.handoffStatusBadge.className = 'badge badge-green';
        }
      } else {
        if (elements.handoffCompletedBanner) elements.handoffCompletedBanner.classList.add('hidden');
        if (elements.handoffStatusBadge) {
          elements.handoffStatusBadge.textContent = 'PENDING REBOOKING';
          elements.handoffStatusBadge.className = 'badge badge-amber';
        }
      }

      try {
        const res = await fetch(`/api/trips/${encodeURIComponent(trip.id)}/handoff`);
        if (!res.ok) {
          return;
        }
        const data = await res.json();
        const legs = (data && data.success && data.data) ? data.data : [];

        if (!elements.handoffLegsList) return;

        if (legs.length === 0) {
          elements.handoffLegsList.innerHTML = '<p class="empty-state">No replacement transport legs to hand off.</p>';
          return;
        }

        elements.handoffLegsList.innerHTML = '';
        legs.forEach((leg) => {
          const card = document.createElement('div');
          card.className = `handoff-leg-card ${leg.booked ? 'leg-confirmed' : ''}`;

          const linksHtml = (leg.links || []).map((l) => `
          <a href="${escapeHtml(l.url)}" target="_blank" rel="noopener noreferrer" class="handoff-link-btn" title="Open provider booking portal">
            <span>🔗</span>
            <span>${escapeHtml(l.label)}</span>
          </a>
        `).join('');

          const refHtml = leg.bookingReference
            ? `<span class="handoff-ref-tag" title="Confirmed reference">Ref: ${escapeHtml(leg.bookingReference)}</span>`
            : '';

          card.innerHTML = `
          <div class="handoff-leg-header">
            <div class="handoff-route">
              <span class="badge ${getStatusBadgeClass(leg.type)}">${escapeHtml(leg.type)}</span>
              <span>${escapeHtml(leg.from)}</span>
              <span class="arrow">&rarr;</span>
              <span>${escapeHtml(leg.to)}</span>
            </div>
            <div>
              <span class="badge ${leg.booked ? 'badge-green' : 'badge-amber'}">${leg.booked ? 'BOOKED' : 'NOT BOOKED'}</span>
              ${refHtml}
            </div>
          </div>
          <div class="handoff-details">
            <span><strong>Provider:</strong> ${escapeHtml(leg.provider || 'Operator')}</span>
            <span><strong>Departure:</strong> ${formatDateTime(leg.startTime)}</span>
            <span><strong>Arrival:</strong> ${formatDateTime(leg.endTime)}</span>
          </div>
          <div class="handoff-note">
            ${escapeHtml(leg.scheduleNote || 'Estimated schedule and fare. Please verify on official provider portal.')}
          </div>
          <div class="handoff-actions">
            <div class="handoff-links">
              ${linksHtml}
            </div>
            <div class="handoff-book-toggle">
              ${leg.booked
              ? `<button type="button" class="btn btn-sm btn-outline-danger btn-unbook-leg" data-booking-id="${escapeHtml(leg.bookingId)}">Unmark / Unbook</button>`
              : `<button type="button" class="btn btn-sm btn-success btn-book-leg" data-booking-id="${escapeHtml(leg.bookingId)}" data-from="${escapeHtml(leg.from)}" data-to="${escapeHtml(leg.to)}" data-type="${escapeHtml(leg.type)}" data-provider="${escapeHtml(leg.provider)}">&#10003; I have booked this</button>`
            }
            </div>
          </div>
        `;

          const btnBook = card.querySelector('.btn-book-leg');
          if (btnBook) {
            btnBook.addEventListener('click', () => {
              openConfirmBookingModal(
                btnBook.getAttribute('data-booking-id'),
                `${btnBook.getAttribute('data-type')} ${btnBook.getAttribute('data-provider') || ''}: ${btnBook.getAttribute('data-from')} &rarr; ${btnBook.getAttribute('data-to')}`
              );
            });
          }

          const btnUnbook = card.querySelector('.btn-unbook-leg');
          if (btnUnbook) {
            btnUnbook.addEventListener('click', () => {
              handleUnmarkBooking(trip.id, btnUnbook.getAttribute('data-booking-id'));
            });
          }

          elements.handoffLegsList.appendChild(card);
        });
      } catch (e) {
        console.warn('Could not load handoff legs:', e);
      }
    }

    function openConfirmBookingModal(bookingId, summaryText) {
      if (elements.confirmBookingId) elements.confirmBookingId.value = bookingId;
      if (elements.confirmBookingSummary) elements.confirmBookingSummary.innerHTML = `<strong>Confirming:</strong> ${summaryText}`;
      if (elements.confirmBookingRef) elements.confirmBookingRef.value = '';
      if (elements.modalConfirmBooking) {
        elements.modalConfirmBooking.classList.remove('hidden');
        elements.modalConfirmBooking.classList.add('is-open');
      }
    }

    function closeConfirmBookingModal() {
      if (elements.modalConfirmBooking) {
        elements.modalConfirmBooking.classList.add('hidden');
        elements.modalConfirmBooking.classList.remove('is-open');
      }
    }

    async function handleSubmitConfirmBooking(e) {
      e.preventDefault();
      const tripId = state.currentTripId;
      const bookingId = elements.confirmBookingId.value;
      const reference = elements.confirmBookingRef.value.trim();
      if (!tripId || !bookingId) return;

      setButtonLoading(elements.btnSubmitConfirmBooking, true);
      try {
        const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/bookings/${encodeURIComponent(bookingId)}/book`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ bookingReference: reference }),
        });
        const data = await res.json();
        if (!res.ok || !data.success) {
          throw new Error(data.message || 'Failed to mark booking as booked');
        }

        closeConfirmBookingModal();
        showToast('Booking marked as booked!', 'success');
        await loadTripById(tripId);
      } catch (err) {
        showToast(err.message, 'danger');
      } finally {
        setButtonLoading(elements.btnSubmitConfirmBooking, false);
      }
    }

    async function handleUnmarkBooking(tripId, bookingId) {
      try {
        const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/bookings/${encodeURIComponent(bookingId)}/unbook`, {
          method: 'POST',
        });
        const data = await res.json();
        if (!res.ok || !data.success) {
          throw new Error(data.message || 'Failed to revert booking status');
        }

        showToast('Booking reverted to rebooked status. Trip moved back to RESOLVED.', 'info');
        await loadTripById(tripId);
      } catch (err) {
        showToast(err.message, 'danger');
      }
    }

    // ==========================================
    // Initialization
    // ==========================================

    function init() {
      initEventListeners();
      fetchCsrfToken();
      checkCurrentUser();
    }

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', init);
    } else {
      init();
    }
  }) ();
