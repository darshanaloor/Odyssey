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
    providerProfile: null,
    providerVehicles: [],
    providerSchedules: [],
    providerDepartures: [],
    adminProviders: [],
  };

  // DOM Elements Cache
  const elements = {
    // Header & Navigation
    tripStatusBadge: document.getElementById('trip-status-badge'),
    btnOpenCreateTrip: document.getElementById('btn-open-create-trip'),
    btnLoadDemo: document.getElementById('btn-load-demo'),
    btnReset: document.getElementById('btn-reset'),
    headerTripStatusGroup: document.getElementById('header-trip-status-group'),
    headerTripActionsGroup: document.getElementById('header-trip-actions-group'),
    btnOpenLogin: document.getElementById('btn-open-login'),
    btnOpenRegister: document.getElementById('btn-open-register'),
    userProfilePill: document.getElementById('user-profile-pill'),
    userDisplayName: document.getElementById('user-display-name'),
    navBtnMarketSearch: document.getElementById('nav-btn-market-search'),
    navBtnMarketBookings: document.getElementById('nav-btn-market-bookings'),
    navMarketBookingsCount: document.getElementById('nav-market-bookings-count'),
    navBtnDirectory: document.getElementById('nav-btn-directory'),
    navBtnWorkspace: document.getElementById('nav-btn-workspace'),
    navBtnProvider: document.getElementById('nav-btn-provider'),
    navBtnAdmin: document.getElementById('nav-btn-admin'),
    navTripsCount: document.getElementById('nav-trips-count'),
    navCurrentTripTag: document.getElementById('nav-current-trip-tag'),

    // Views
    sectionMarketSearch: document.getElementById('section-market-search'),
    sectionMarketBookings: document.getElementById('section-market-bookings'),
    sectionTripsDirectory: document.getElementById('section-trips-directory'),
    sectionWorkspace: document.getElementById('section-workspace'),
    sectionProviderPortal: document.getElementById('section-provider-portal'),
    sectionAdminPortal: document.getElementById('section-admin-portal'),

    // Provider Portal Panels & Controls
    panelProviderRegister: document.getElementById('panel-provider-register'),
    panelProviderDashboard: document.getElementById('panel-provider-dashboard'),
    formProviderRegister: document.getElementById('form-provider-register'),
    providerStatusBanner: document.getElementById('provider-status-banner'),
    btnProvTabVehicles: document.getElementById('btn-prov-tab-vehicles'),
    btnProvTabSchedules: document.getElementById('btn-prov-tab-schedules'),
    btnProvTabDepartures: document.getElementById('btn-prov-tab-departures'),
    provTabContentVehicles: document.getElementById('prov-tab-content-vehicles'),
    provTabContentSchedules: document.getElementById('prov-tab-content-schedules'),
    provTabContentDepartures: document.getElementById('prov-tab-content-departures'),
    provVehiclesCount: document.getElementById('prov-vehicles-count'),
    provSchedulesCount: document.getElementById('prov-schedules-count'),
    provDeparturesCount: document.getElementById('prov-departures-count'),
    provVehiclesTbody: document.getElementById('prov-vehicles-tbody'),
    provSchedulesTbody: document.getElementById('prov-schedules-tbody'),
    provDeparturesTbody: document.getElementById('prov-departures-tbody'),
    btnProvAddVehicle: document.getElementById('btn-prov-add-vehicle'),
    btnProvAddSchedule: document.getElementById('btn-prov-add-schedule'),
    btnRefreshProvDepartures: document.getElementById('btn-refresh-prov-departures'),

    // Modals: Vehicle, Schedule, Status, Bookings, Admin Action
    modalProvVehicle: document.getElementById('modal-prov-vehicle'),
    btnCloseProvVehicleModal: document.getElementById('btn-close-prov-vehicle-modal'),
    btnCancelProvVehicle: document.getElementById('btn-cancel-prov-vehicle'),
    formProvVehicle: document.getElementById('form-prov-vehicle'),

    modalProvSchedule: document.getElementById('modal-prov-schedule'),
    btnCloseProvScheduleModal: document.getElementById('btn-close-prov-schedule-modal'),
    btnCancelProvSchedule: document.getElementById('btn-cancel-prov-schedule'),
    formProvSchedule: document.getElementById('form-prov-schedule'),

    modalProvStatus: document.getElementById('modal-prov-status'),
    btnCloseProvStatusModal: document.getElementById('btn-close-prov-status-modal'),
    btnCancelProvStatus: document.getElementById('btn-cancel-prov-status'),
    formProvStatus: document.getElementById('form-prov-status'),
    provStatusDepId: document.getElementById('prov-status-dep-id'),
    provStatusDepSummary: document.getElementById('prov-status-dep-summary'),
    provStatusState: document.getElementById('prov-status-state'),
    provStatusDelayGroup: document.getElementById('prov-status-delay-group'),
    provStatusDelay: document.getElementById('prov-status-delay'),
    provStatusReason: document.getElementById('prov-status-reason'),

    modalProvBookings: document.getElementById('modal-prov-bookings'),
    btnCloseProvBookingsModal: document.getElementById('btn-close-prov-bookings-modal'),
    btnCloseProvBookings: document.getElementById('btn-close-prov-bookings'),
    provBookingsTbody: document.getElementById('prov-bookings-tbody'),
    provBookingsDepSummary: document.getElementById('prov-bookings-dep-summary'),
    provCancellationsTbody: document.getElementById('prov-cancellations-tbody'),

    modalProvOfferAlt: document.getElementById('modal-prov-offer-alternative'),
    btnCloseProvOfferAltModal: document.getElementById('btn-close-prov-offer-alt-modal'),
    btnCancelProvOfferAlt: document.getElementById('btn-cancel-prov-offer-alt'),
    formProvOfferAlt: document.getElementById('form-prov-offer-alt'),
    provOfferAltDepId: document.getElementById('prov-offer-alt-dep-id'),
    provOfferAltDepSummary: document.getElementById('prov-offer-alt-dep-summary'),
    provOfferAltSelect: document.getElementById('prov-offer-alt-select'),
    provOfferAltNewVehicleSection: document.getElementById('prov-offer-alt-new-vehicle-section'),
    provOfferAltNewNumber: document.getElementById('prov-offer-alt-new-number'),
    provOfferAltNewSeats: document.getElementById('prov-offer-alt-new-seats'),
    provOfferAltNewType: document.getElementById('prov-offer-alt-new-type'),
    provOfferAltReason: document.getElementById('prov-offer-alt-reason'),
    btnSubmitProvOfferAlt: document.getElementById('btn-submit-prov-offer-alt'),

    modalAdminAction: document.getElementById('modal-admin-action'),
    btnCloseAdminActionModal: document.getElementById('btn-close-admin-action-modal'),
    btnCancelAdminAction: document.getElementById('btn-cancel-admin-action'),
    formAdminAction: document.getElementById('form-admin-action'),
    adminActionProvId: document.getElementById('admin-action-prov-id'),
    adminActionType: document.getElementById('admin-action-type'),
    adminActionPrompt: document.getElementById('admin-action-prompt'),
    adminActionReason: document.getElementById('admin-action-reason'),
    adminProvidersTbody: document.getElementById('admin-providers-tbody'),
    btnRefreshAdminProviders: document.getElementById('btn-refresh-admin-providers'),

    // Phase 18B: Provider Hotels, Rooms, Cabs, Availability, Manifest
    btnProvTabHotels: document.getElementById('btn-prov-tab-hotels'),
    btnProvTabCabs: document.getElementById('btn-prov-tab-cabs'),
    provTabContentHotels: document.getElementById('prov-tab-content-hotels'),
    provTabContentCabs: document.getElementById('prov-tab-content-cabs'),
    provHotelsCount: document.getElementById('prov-hotels-count'),
    provCabsCount: document.getElementById('prov-cabs-count'),
    provHotelsTbody: document.getElementById('prov-hotels-tbody'),
    provHotelRoomsSection: document.getElementById('prov-hotel-rooms-section'),
    provSelectedHotelTitle: document.getElementById('prov-selected-hotel-title'),
    provRoomTypesTbody: document.getElementById('prov-room-types-tbody'),
    provCabsTbody: document.getElementById('prov-cabs-tbody'),
    btnProvAddHotel: document.getElementById('btn-prov-add-hotel'),
    btnProvAddRoomType: document.getElementById('btn-prov-add-room-type'),
    btnProvCloseRoomsView: document.getElementById('btn-prov-close-rooms-view'),
    btnProvAddCab: document.getElementById('btn-prov-add-cab'),

    modalProvHotel: document.getElementById('modal-prov-hotel'),
    btnCloseProvHotelModal: document.getElementById('btn-close-prov-hotel-modal'),
    btnCancelProvHotel: document.getElementById('btn-cancel-prov-hotel'),
    formProvHotel: document.getElementById('form-prov-hotel'),

    modalProvRoomType: document.getElementById('modal-prov-room-type'),
    btnCloseProvRoomModal: document.getElementById('btn-close-prov-room-modal'),
    btnCancelProvRoom: document.getElementById('btn-cancel-prov-room'),
    formProvRoomType: document.getElementById('form-prov-room-type'),

    modalProvAvailability: document.getElementById('modal-prov-availability'),
    btnCloseProvAvailModal: document.getElementById('btn-close-prov-avail-modal'),
    btnCloseProvAvail: document.getElementById('btn-close-prov-avail'),
    provAvailRoomSummary: document.getElementById('prov-avail-room-summary'),
    provAvailTbody: document.getElementById('prov-avail-tbody'),

    modalProvCab: document.getElementById('modal-prov-cab'),
    btnCloseProvCabModal: document.getElementById('btn-close-prov-cab-modal'),
    btnCancelProvCab: document.getElementById('btn-cancel-prov-cab'),
    formProvCab: document.getElementById('form-prov-cab'),

    modalProvHotelBookings: document.getElementById('modal-prov-hotel-bookings'),
    btnCloseProvHotelBkgModal: document.getElementById('btn-close-prov-hotel-bkg-modal'),
    btnCloseProvHotelBookings: document.getElementById('btn-close-prov-hotel-bookings'),
    provHotelBookingsTbody: document.getElementById('prov-hotel-bookings-tbody'),

    modalProvCannotHonour: document.getElementById('modal-prov-cannot-honour'),
    btnCloseProvHonourModal: document.getElementById('btn-close-prov-honour-modal'),
    btnCancelProvHonour: document.getElementById('btn-cancel-prov-honour'),
    formProvCannotHonour: document.getElementById('form-prov-cannot-honour'),
    provCannotHonourBkgId: document.getElementById('prov-cannot-honour-bkg-id'),
    provCannotHonourReason: document.getElementById('prov-cannot-honour-reason'),

    // Phase 18C: Provider Refunds
    btnProvTabRefunds: document.getElementById('btn-prov-tab-refunds'),
    provTabContentRefunds: document.getElementById('prov-tab-content-refunds'),
    provRefundsTbody: document.getElementById('prov-refunds-tbody'),
    btnRefreshProvRefunds: document.getElementById('btn-refresh-prov-refunds'),
    modalProvRefund: document.getElementById('modal-prov-refund'),
    btnCloseProvRefundModal: document.getElementById('btn-close-prov-refund-modal'),
    btnCancelProvRefund: document.getElementById('btn-cancel-prov-refund'),
    formProvRefund: document.getElementById('form-prov-refund'),
    provRefundBookingId: document.getElementById('prov-refund-booking-id'),
    provRefundBookingRef: document.getElementById('prov-refund-booking-ref'),
    provRefundOriginalCost: document.getElementById('prov-refund-original-cost'),
    provRefundAmount: document.getElementById('prov-refund-amount'),
    provRefundReason: document.getElementById('prov-refund-reason'),
    provRefundErrorMsg: document.getElementById('prov-refund-error-msg'),
    btnSubmitProvRefund: document.getElementById('btn-submit-prov-refund'),

    // Provider Pending Confirmations
    btnProvTabPending: document.getElementById('btn-prov-tab-pending'),
    provTabContentPending: document.getElementById('prov-tab-content-pending'),
    provPendingCount: document.getElementById('prov-pending-count'),
    provPendingTbody: document.getElementById('prov-pending-tbody'),
    btnRefreshProvPending: document.getElementById('btn-refresh-prov-pending'),

    // Marketplace Elements
    tabMarketTransport: document.getElementById('tab-market-transport'),
    tabMarketHotels: document.getElementById('tab-market-hotels'),
    tabMarketCabs: document.getElementById('tab-market-cabs'),
    subpanelMarketTransport: document.getElementById('subpanel-market-transport'),
    subpanelMarketHotels: document.getElementById('subpanel-market-hotels'),
    subpanelMarketCabs: document.getElementById('subpanel-market-cabs'),

    formMarketSearch: document.getElementById('form-market-search'),
    marketSearchOrigin: document.getElementById('market-search-origin'),
    originSuggestions: document.getElementById('origin-suggestions'),
    originFuzzyBox: document.getElementById('origin-fuzzy-box'),
    marketSearchDest: document.getElementById('market-search-dest'),
    destSuggestions: document.getElementById('dest-suggestions'),
    destFuzzyBox: document.getElementById('dest-fuzzy-box'),
    marketPopularRoutes: document.getElementById('market-popular-routes'),
    marketSearchDate: document.getElementById('market-search-date'),
    marketSearchMode: document.getElementById('market-search-mode'),
    btnMarketSearch: document.getElementById('btn-market-search'),
    marketServicesList: document.getElementById('market-services-list'),
    marketServicesCount: document.getElementById('market-services-count'),

    formMarketHotels: document.getElementById('form-market-hotels'),
    marketHotelCity: document.getElementById('market-hotel-city'),
    marketHotelCheckin: document.getElementById('market-hotel-checkin'),
    marketHotelCheckout: document.getElementById('market-hotel-checkout'),
    marketHotelRooms: document.getElementById('market-hotel-rooms'),
    btnMarketHotelsSearch: document.getElementById('btn-market-hotels-search'),
    marketHotelsList: document.getElementById('market-hotels-list'),

    formMarketCabs: document.getElementById('form-market-cabs'),
    marketCabCity: document.getElementById('market-cab-city'),
    marketCabPickup: document.getElementById('market-cab-pickup'),
    marketCabDropoff: document.getElementById('market-cab-dropoff'),
    btnMarketCabsQuote: document.getElementById('btn-market-cabs-quote'),
    marketCabsList: document.getElementById('market-cabs-list'),

    btnRefreshMarketBookings: document.getElementById('btn-refresh-market-bookings'),
    btnSwitchToSearch: document.getElementById('btn-switch-to-search'),
    marketMyBookingsCount: document.getElementById('market-my-bookings-count'),
    marketBookingsContainer: document.getElementById('market-bookings-container'),

    // Modals
    modalSeatSelection: document.getElementById('modal-seat-selection'),
    btnCloseSeatModal: document.getElementById('btn-close-seat-modal'),
    btnCancelSeatSelection: document.getElementById('btn-cancel-seat-selection'),
    btnProceedToCheckout: document.getElementById('btn-proceed-to-checkout'),
    seatModalServiceSummary: document.getElementById('seat-modal-service-summary'),
    seatGrid: document.getElementById('seat-grid'),
    flightSeatSelector: document.getElementById('flight-seat-selector'),
    flightPassengerCount: document.getElementById('flight-passenger-count'),
    passengerInputsContainer: document.getElementById('passenger-inputs-container'),

    modalCheckout: document.getElementById('modal-checkout'),
    btnCloseCheckoutModal: document.getElementById('btn-close-checkout-modal'),
    btnCancelCheckout: document.getElementById('btn-cancel-checkout'),
    formMockPayment: document.getElementById('form-mock-payment'),
    checkoutOrderSummary: document.getElementById('checkout-order-summary'),
    cardHolderName: document.getElementById('card-holder-name'),
    cardNumber: document.getElementById('card-number'),
    cardExpiry: document.getElementById('card-expiry'),
    cardCvv: document.getElementById('card-cvv'),
    btnPayNow: document.getElementById('btn-pay-now'),
    btnPayNowText: document.getElementById('btn-pay-now-text'),
    checkoutErrorMsg: document.getElementById('checkout-error-msg'),
    choiceCreateTrip: document.getElementById('choice-create-trip'),
    choiceExistingTrip: document.getElementById('choice-existing-trip'),
    checkoutExistingTripContainer: document.getElementById('checkout-existing-trip-container'),
    checkoutTripSelect: document.getElementById('checkout-trip-select'),
    checkoutAddonsSection: document.getElementById('checkout-addons-section'),
    checkoutAddonCabToggle: document.getElementById('checkout-addon-cab-toggle'),
    checkoutAddonCabContainer: document.getElementById('checkout-addon-cab-container'),
    checkoutCabPickup: document.getElementById('checkout-cab-pickup'),
    checkoutCabDropoff: document.getElementById('checkout-cab-dropoff'),
    checkoutAddonHotelToggle: document.getElementById('checkout-addon-hotel-toggle'),
    checkoutAddonHotelContainer: document.getElementById('checkout-addon-hotel-container'),
    checkoutHotelName: document.getElementById('checkout-hotel-name'),
    checkoutHotelNights: document.getElementById('checkout-hotel-nights'),
    checkoutHotelRooms: document.getElementById('checkout-hotel-rooms'),
    checkoutCabOptionsList: document.getElementById('checkout-cab-options-list'),
    checkoutSelectedCabId: document.getElementById('checkout-selected-cab-id'),
    checkoutHotelOptionsList: document.getElementById('checkout-hotel-options-list'),
    checkoutSelectedHotelId: document.getElementById('checkout-selected-hotel-id'),
    checkoutSelectedRoomTypeId: document.getElementById('checkout-selected-room-type-id'),

    // Modal Pending Confirmation
    modalProviderPending: document.getElementById('modal-provider-pending'),
    pendingCountdownTimer: document.getElementById('pending-countdown-timer'),
    pendingProgressBar: document.getElementById('pending-progress-bar'),
    pendingProviderMessage: document.getElementById('pending-provider-message'),
    pendingActionFeedback: document.getElementById('pending-action-feedback'),
    btnCancelPendingRequest: document.getElementById('btn-cancel-pending-request'),

    paymentMethodCard: document.getElementById('payment-method-card'),
    paymentMethodUpi: document.getElementById('payment-method-upi'),
    paymentMethodNetbanking: document.getElementById('payment-method-netbanking'),
    checkoutCardFields: document.getElementById('checkout-card-fields'),
    checkoutUpiFields: document.getElementById('checkout-upi-fields'),
    checkoutNetbankingFields: document.getElementById('checkout-netbanking-fields'),
    checkoutUpiId: document.getElementById('checkout-upi-id'),
    checkoutBankSelect: document.getElementById('checkout-bank-select'),

    modalConfirmation: document.getElementById('modal-confirmation'),
    btnCloseConfirmationModal: document.getElementById('btn-close-confirmation-modal'),
    confirmedBookingRef: document.getElementById('confirmed-booking-ref'),
    confirmedTripInfo: document.getElementById('confirmed-trip-info'),
    confirmedTripTitle: document.getElementById('confirmed-trip-title'),
    btnOpenConfirmedTrip: document.getElementById('btn-open-confirmed-trip'),
    confirmedBookingDetails: document.getElementById('confirmed-booking-details'),
    btnDownloadConfirmedSummary: document.getElementById('btn-download-confirmed-summary'),
    btnConfirmGoToBookings: document.getElementById('btn-confirm-go-to-bookings'),

    modalCancelBooking: document.getElementById('modal-cancel-booking'),
    btnCloseCancelModal: document.getElementById('btn-close-cancel-modal'),
    btnDismissCancelBooking: document.getElementById('btn-dismiss-cancel-booking'),
    btnExecuteCancelBooking: document.getElementById('btn-execute-cancel-booking'),
    cancelModalRef: document.getElementById('cancel-modal-ref'),
    cancelModalPolicyNotice: document.getElementById('cancel-modal-policy-notice'),

    // Phase 10C: Simulation & Replacement Modals
    modalSimulateDeparture: document.getElementById('modal-simulate-departure'),
    btnCloseSimulateModal: document.getElementById('btn-close-simulate-modal'),
    btnDismissSimulate: document.getElementById('btn-dismiss-simulate'),
    btnApplyDepSimulation: document.getElementById('btn-apply-dep-simulation'),
    simulateDepartureInfo: document.getElementById('simulate-departure-info'),
    simulateStatusSelect: document.getElementById('simulate-status-select'),
    simulateDelayGroup: document.getElementById('simulate-delay-group'),
    simulateDelayMinutes: document.getElementById('simulate-delay-minutes'),
    simulateErrorMsg: document.getElementById('simulate-error-msg'),

    modalReplacementBooking: document.getElementById('modal-replacement-booking'),
    btnCloseReplacementModal: document.getElementById('btn-close-replacement-modal'),
    btnDismissReplacement: document.getElementById('btn-dismiss-replacement'),
    btnConfirmBookReplacement: document.getElementById('btn-confirm-book-replacement'),
    replacementPreviewContent: document.getElementById('replacement-preview-content'),
    replacementErrorMsg: document.getElementById('replacement-error-msg'),

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
    tripSubnavBar: document.getElementById('trip-subnav-bar'),
    tabTripOverview: document.getElementById('tab-trip-overview'),
    tabTripDisruption: document.getElementById('tab-trip-disruption'),
    tabTripRecovery: document.getElementById('tab-trip-recovery'),
    tabTripPlan: document.getElementById('tab-trip-plan'),
    tabTripNotifications: document.getElementById('tab-trip-notifications'),
    tabTripLedger: document.getElementById('tab-trip-ledger'),
    linkTripWallet: document.getElementById('link-trip-wallet'),
    sectionTripSummary: document.getElementById('section-trip-summary'),
    sectionTimeline: document.getElementById('section-timeline'),
    sectionDisruption: document.getElementById('section-disruption'),
    sectionRecovery: document.getElementById('section-recovery'),
    sectionImport: document.getElementById('section-import'),
    sectionAudit: document.getElementById('section-audit'),
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
    overviewTravellerNeeds: document.getElementById('overview-traveller-needs'),
    tripTravellerNeeds: document.getElementById('trip-traveller-needs'),
    overviewEmergencyContact: document.getElementById('overview-emergency-contact'),
    tripEmergencyContact: document.getElementById('trip-emergency-contact'),
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
    btnCheckStatusTop: document.getElementById('btn-check-status-top'),
    liveStatusBookingsTbody: document.getElementById('live-status-bookings-tbody'),
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
    planEmptyState: document.getElementById('plan-empty-state'),
    planContentWrap: document.getElementById('plan-content-wrap'),
    btnPlanGotoRecovery: document.getElementById('btn-plan-goto-recovery'),
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

    // Phase 11: Recovery Progress Tracker
    sectionRecoveryTracker: document.getElementById('section-recovery-tracker'),
    trackerProgressBadge: document.getElementById('tracker-progress-badge'),
    trackerProgressBarFill: document.getElementById('tracker-progress-bar-fill'),
    trackerOrderNotice: document.getElementById('tracker-order-notice'),
    trackerActionsList: document.getElementById('tracker-actions-list'),

    // Phase 12: Cost and Refund Ledger
    sectionLedger: document.getElementById('section-ledger'),
    btnExportLedgerCsv: document.getElementById('btn-export-ledger-csv'),
    ledgerSumOriginal: document.getElementById('ledger-sum-original'),
    ledgerSumReplacement: document.getElementById('ledger-sum-replacement'),
    ledgerSumExtra: document.getElementById('ledger-sum-extra'),
    ledgerSumExpectedRefunds: document.getElementById('ledger-sum-expected-refunds'),
    ledgerSumRecordedRefunds: document.getElementById('ledger-sum-recorded-refunds'),
    ledgerSumOutstandingRefunds: document.getElementById('ledger-sum-outstanding-refunds'),
    ledgerSumNet: document.getElementById('ledger-sum-net'),
    ledgerTimingNote: document.getElementById('ledger-timing-note'),
    ledgerBookingSummariesWrap: document.getElementById('ledger-booking-summaries-wrap'),
    ledgerBookingSummariesBody: document.getElementById('ledger-booking-summaries-body'),
    ledgerTableBody: document.getElementById('ledger-table-body'),
    ledgerRefundBookingSelect: document.getElementById('ledger-refund-booking-select'),
    ledgerRefundAmountInput: document.getElementById('ledger-refund-amount-input'),
    ledgerRefundStatusSelect: document.getElementById('ledger-refund-status-select'),
    ledgerRefundDateInput: document.getElementById('ledger-refund-date-input'),
    ledgerRefundRefInput: document.getElementById('ledger-refund-ref-input'),
    ledgerRefundNoteInput: document.getElementById('ledger-refund-note-input'),
    btnAddDeductionRow: document.getElementById('btn-add-deduction-row'),
    refundDeductionsList: document.getElementById('refund-deductions-list'),
    refundLiveNetPreview: document.getElementById('refund-live-net-preview'),
    refundLiveReductionPreview: document.getElementById('refund-live-reduction-preview'),
    refundFormError: document.getElementById('refund-form-error'),
    btnSubmitRecordRefund: document.getElementById('btn-submit-record-refund'),

    // Modal: Share Refund Summary
    modalShareRefund: document.getElementById('modal-share-refund'),
    btnCloseShareModal: document.getElementById('btn-close-share-modal'),
    btnCancelShareRefund: document.getElementById('btn-cancel-share-refund'),
    btnConfirmShareRefund: document.getElementById('btn-confirm-share-refund'),
    shareRefundEntryId: document.getElementById('share-refund-entry-id'),
    shareRefundChannelSelect: document.getElementById('share-refund-channel-select'),
    shareRefundRecipientGroup: document.getElementById('share-refund-recipient-group'),
    shareRefundRecipientInput: document.getElementById('share-refund-recipient-input'),
    shareRefundPreview: document.getElementById('share-refund-preview'),
    shareRefundConfirmCheck: document.getElementById('share-refund-confirm-check'),
    shareRefundStatusBanner: document.getElementById('share-refund-status-banner'),

    // Phase 13: Communications Assistant
    sectionMessages: document.getElementById('section-messages'),
    messagesCountBadge: document.getElementById('messages-count-badge'),
    selectMsgRecipientType: document.getElementById('select-msg-recipient-type'),
    selectMsgDraftKind: document.getElementById('select-msg-draft-kind'),
    selectMsgBooking: document.getElementById('select-msg-booking'),
    btnGenerateDraft: document.getElementById('btn-generate-draft'),
    msgActiveEditorContainer: document.getElementById('msg-active-editor-container'),
    badgeMsgGeneratedBy: document.getElementById('badge-msg-generated-by'),
    badgeMsgStatus: document.getElementById('badge-msg-status'),
    inputActiveMsgId: document.getElementById('input-active-msg-id'),
    inputMsgSubject: document.getElementById('input-msg-subject'),
    textareaMsgBody: document.getElementById('textarea-msg-body'),
    msgCharCounter: document.getElementById('msg-char-counter'),
    btnSaveMsgEdits: document.getElementById('btn-save-msg-edits'),
    selectMsgChannel: document.getElementById('select-msg-channel'),
    inputMsgRecipient: document.getElementById('input-msg-recipient'),
    btnSendMessageOpen: document.getElementById('btn-send-message-open'),
    msgSendConfirmBox: document.getElementById('msg-send-confirm-box'),
    btnConfirmSendMessage: document.getElementById('btn-confirm-send-message'),
    btnCancelSendMessage: document.getElementById('btn-cancel-send-message'),
    msgDispatchResultBanner: document.getElementById('msg-dispatch-result-banner'),
    msgDispatchResultText: document.getElementById('msg-dispatch-result-text'),
    messagesTableBody: document.getElementById('messages-table-body'),

    // Phase 14: Trip Wallet
    btnViewWallet: document.getElementById('btn-view-wallet'),
    sectionWallet: document.getElementById('section-wallet'),
    btnWalletPrint: document.getElementById('btn-wallet-print'),
    btnWalletCalendar: document.getElementById('btn-wallet-calendar'),
    btnWalletPack: document.getElementById('btn-wallet-pack'),
    walletLedgerSummaryLine: document.getElementById('wallet-ledger-summary-line'),
    walletBookingsContainer: document.getElementById('wallet-bookings-container'),
    walletIncludeEmergency: document.getElementById('wallet-include-emergency'),

    // Phase 15: Trip Readiness
    btnViewReadiness: document.getElementById('btn-view-readiness'),
    btnRefreshReadiness: document.getElementById('btn-refresh-readiness'),
    sectionReadiness: document.getElementById('section-readiness'),
    readinessScoreDisplay: document.getElementById('readiness-score-display'),
    readinessBandBadge: document.getElementById('readiness-band-badge'),
    readinessReliabilityMsg: document.getElementById('readiness-reliability-msg'),
    readinessFactorsTbody: document.getElementById('readiness-factors-tbody'),
    readinessUnknownContainer: document.getElementById('readiness-unknown-container'),
    readinessUnknownList: document.getElementById('readiness-unknown-list'),
    readinessRecommendationsList: document.getElementById('readiness-recommendations-list'),

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
    createTripNeedMobility: document.getElementById('create-trip-need-mobility'),
    createTripNeedElderly: document.getElementById('create-trip-need-elderly'),
    createTripNeedChildren: document.getElementById('create-trip-need-children'),
    createTripNeedMedical: document.getElementById('create-trip-need-medical'),
    createTripNeedsNote: document.getElementById('create-trip-needs-note'),
    createTripEmergencyName: document.getElementById('create-trip-emergency-name'),
    createTripEmergencyPhone: document.getElementById('create-trip-emergency-phone'),
    errCreateNeedsNote: document.getElementById('err-create-needs-note'),
    errCreateEmergencyPhone: document.getElementById('err-create-emergency-phone'),

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
    editTripNeedMobility: document.getElementById('edit-trip-need-mobility'),
    editTripNeedElderly: document.getElementById('edit-trip-need-elderly'),
    editTripNeedChildren: document.getElementById('edit-trip-need-children'),
    editTripNeedMedical: document.getElementById('edit-trip-need-medical'),
    editTripNeedsNote: document.getElementById('edit-trip-needs-note'),
    editTripEmergencyName: document.getElementById('edit-trip-emergency-name'),
    editTripEmergencyPhone: document.getElementById('edit-trip-emergency-phone'),
    errEditNeedsNote: document.getElementById('err-edit-needs-note'),
    errEditEmergencyPhone: document.getElementById('err-edit-emergency-phone'),

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

    // Phase 18D Dual Auth Role Switchers & Provider Forms
    tabAuthTraveller: document.getElementById('tab-auth-traveller'),
    tabAuthProvider: document.getElementById('tab-auth-provider'),
    authSectionTraveller: document.getElementById('auth-section-traveller'),
    authSectionProvider: document.getElementById('auth-section-provider'),
    tabProvAuthLogin: document.getElementById('tab-prov-auth-login'),
    tabProvAuthRegister: document.getElementById('tab-prov-auth-register'),
    formProviderLogin: document.getElementById('form-provider-login'),
    provLoginEmail: document.getElementById('prov-login-email'),
    provLoginPassword: document.getElementById('prov-login-password'),
    errProviderLogin: document.getElementById('err-provider-login'),
    btnSubmitProviderLogin: document.getElementById('btn-submit-provider-login'),
    linkGotoProvRegister: document.getElementById('link-goto-prov-register'),
    formProviderSignup: document.getElementById('form-provider-signup'),
    provSignupName: document.getElementById('prov-signup-name'),
    provSignupPhone: document.getElementById('prov-signup-phone'),
    provSignupEmail: document.getElementById('prov-signup-email'),
    provSignupPassword: document.getElementById('prov-signup-password'),
    provSignupBizName: document.getElementById('prov-signup-biz-name'),
    provSignupBizType: document.getElementById('prov-signup-biz-type'),
    provSignupCity: document.getElementById('prov-signup-city'),
    provSignupDesc: document.getElementById('prov-signup-desc'),
    errProviderSignup: document.getElementById('err-provider-signup'),
    btnSubmitProviderSignup: document.getElementById('btn-submit-provider-signup'),
    linkGotoProvLoginFromReg: document.getElementById('link-goto-prov-login-from-reg'),

    modalRegister: document.getElementById('modal-register'),
    btnCloseRegisterModal: document.getElementById('btn-close-register-modal'),
    tabRegTraveller: document.getElementById('tab-reg-traveller'),
    tabRegProvider: document.getElementById('tab-reg-provider'),
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

    // Secondary Provider Upgrade for Existing Travellers
    accountProviderUpgradeSection: document.getElementById('account-provider-upgrade-section'),
    btnOpenProviderUpgrade: document.getElementById('btn-open-provider-upgrade'),
    modalProviderApply: document.getElementById('modal-provider-apply'),
    btnCloseProvApplyModal: document.getElementById('btn-close-prov-apply-modal'),
    formAccountProviderApply: document.getElementById('form-account-provider-apply'),
    errAccountProviderApply: document.getElementById('err-account-provider-apply'),
    btnSubmitAccountProviderApply: document.getElementById('btn-submit-account-provider-apply'),

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
    sessionStorage.removeItem('odyssey_auth_token');
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

    // Tab-isolated session token injection
    const authToken = sessionStorage.getItem('odyssey_auth_token');
    if (authToken) {
      if (options.headers instanceof Headers) {
        if (!options.headers.has('X-Auth-Token')) options.headers.set('X-Auth-Token', authToken);
        if (!options.headers.has('Authorization')) options.headers.set('Authorization', 'Bearer ' + authToken);
      } else {
        if (!options.headers['X-Auth-Token']) options.headers['X-Auth-Token'] = authToken;
        if (!options.headers['Authorization']) options.headers['Authorization'] = 'Bearer ' + authToken;
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
    const toastType = type === 'danger' ? 'error' : type;
    const toast = document.createElement('div');
    toast.className = `toast toast-${toastType}`;
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

  // ==========================================
  // Client-Side Router (Phase 18C)
  // ==========================================

  function navigateTo(url, replace = false) {
    if (replace) {
      window.history.replaceState({}, '', url);
    } else {
      window.history.pushState({}, '', url);
    }
    handleRoute();
  }

  async function handleRoute() {
    stopStatusPolling();
    const path = window.location.pathname;

    // Role-based route guard: pure PROVIDER visiting traveller search or trips gets redirected to /provider
    if ((path === '/search' || path === '' || path === '/' || path === '/trips') && state.currentUser && state.currentUser.role === 'PROVIDER') {
      navigateTo('/provider', true);
      return;
    }

    if (path === '/search' || path === '' || path === '/') {
      showMainSection('market-search');
      return;
    }

    if (path.startsWith('/checkout')) {
      const parts = path.split('/').filter(Boolean);
      const draftId = parts[1];
      showMainSection('market-search');
      if (draftId) {
        try {
          const res = await fetch(`/api/market/checkout/draft/${encodeURIComponent(draftId)}`);
          if (res.ok) {
            const data = await res.json();
            if (data.success && data.data) {
              const draft = data.data;
              if (draft.serviceDepartureId) {
                marketState.currentService = {
                  departureId: draft.serviceDepartureId,
                  price: draft.price || 500,
                  operator: draft.operator || 'Odyssey Transport',
                  vehicleNumber: draft.vehicleNumber || 'OD-101',
                  originCity: draft.originCity || 'Athens',
                  destinationCity: draft.destinationCity || 'Sparta',
                  departureTime: draft.departureTime || '10:00'
                };
                marketState.passengers = draft.passengers || [{ name: (state.currentUser ? state.currentUser.name : 'Passenger 1'), seatNumber: '1', age: 28 }];
                openCheckoutModal();
                return;
              }
            }
          }
        } catch (e) {
          console.warn('Failed to load draft:', e);
        }
      }
      if (marketState.currentService || marketState.pendingHotel || marketState.pendingCab) {
        openCheckoutModal();
      } else {
        navigateTo('/search', true);
      }
      return;
    }

    if (path === '/trips') {
      showMainSection('directory');
      fetchTripsDirectory();
      return;
    }

    if (path.startsWith('/trips/')) {
      const parts = path.substring('/trips/'.length).split('/');
      const tripId = parts[0];
      const subroute = parts[1] || '';

      if (!tripId) {
        navigateTo('/trips', true);
        return;
      }

      await loadTripById(tripId, subroute);
      return;
    }

    if (path.startsWith('/provider')) {
      if (!state.currentUser) {
        showToast('Please sign in with a provider account to access the Provider Portal.', 'info');
        openLoginModal('PROVIDER');
        navigateTo('/search', true);
        return;
      }
      if (state.currentUser.role === 'TRAVELLER') {
        showToast('The Provider Portal is only accessible to registered provider accounts. You can apply in Account Settings.', 'info');
        navigateTo(state.trips && state.trips.length > 0 ? '/trips' : '/search', true);
        return;
      }
      showMainSection('provider');
      const parts = path.split('/').filter(Boolean);
      const subtab = parts[1] || 'vehicles';
      switchProviderTab(subtab);
      loadProviderPortal();
      if (subtab === 'refunds') {
        loadProviderRefunds();
      }
      return;
    }

    if (path === '/admin') {
      if (!state.currentUser || state.currentUser.role !== 'ADMIN') {
        showToast('Access denied: Administrator role required.', 'danger');
        navigateTo('/trips', true);
        return;
      }
      showMainSection('admin');
      loadAdminProviders();
      return;
    }

    showMainSection('directory');
  }

  function showMainSection(sectionName) {
    stopStatusPolling();

    if (elements.sectionMarketSearch) elements.sectionMarketSearch.classList.add('hidden');
    if (elements.sectionMarketBookings) elements.sectionMarketBookings.classList.add('hidden');
    if (elements.sectionTripsDirectory) elements.sectionTripsDirectory.classList.add('hidden');
    if (elements.sectionWorkspace) elements.sectionWorkspace.classList.add('hidden');
    if (elements.sectionProviderPortal) elements.sectionProviderPortal.classList.add('hidden');
    if (elements.sectionAdminPortal) elements.sectionAdminPortal.classList.add('hidden');

    if (elements.navBtnMarketSearch) elements.navBtnMarketSearch.classList.remove('active');
    if (elements.navBtnMarketBookings) elements.navBtnMarketBookings.classList.remove('active');
    if (elements.navBtnDirectory) elements.navBtnDirectory.classList.remove('active');
    if (elements.navBtnWorkspace) elements.navBtnWorkspace.classList.remove('active');
    if (elements.navBtnProvider) elements.navBtnProvider.classList.remove('active');
    if (elements.navBtnAdmin) elements.navBtnAdmin.classList.remove('active');

    if (sectionName === 'market-search') {
      if (elements.sectionMarketSearch) elements.sectionMarketSearch.classList.remove('hidden');
      if (elements.navBtnMarketSearch) elements.navBtnMarketSearch.classList.add('active');
    } else if (sectionName === 'market-bookings') {
      if (elements.sectionMarketBookings) elements.sectionMarketBookings.classList.remove('hidden');
      if (elements.navBtnMarketBookings) elements.navBtnMarketBookings.classList.add('active');
    } else if (sectionName === 'directory') {
      if (elements.sectionTripsDirectory) elements.sectionTripsDirectory.classList.remove('hidden');
      if (elements.navBtnDirectory) elements.navBtnDirectory.classList.add('active');
    } else if (sectionName === 'provider') {
      if (elements.sectionProviderPortal) elements.sectionProviderPortal.classList.remove('hidden');
      if (elements.navBtnProvider) elements.navBtnProvider.classList.add('active');
    } else if (sectionName === 'admin') {
      if (elements.sectionAdminPortal) elements.sectionAdminPortal.classList.remove('hidden');
      if (elements.navBtnAdmin) elements.navBtnAdmin.classList.add('active');
    } else if (sectionName === 'workspace') {
      if (elements.sectionWorkspace) elements.sectionWorkspace.classList.remove('hidden');
      if (elements.navBtnWorkspace) elements.navBtnWorkspace.classList.add('active');
    }
  }

  function switchProviderTab(subtab) {
    const tabMap = {
      vehicles: { btn: elements.btnProvTabVehicles, content: elements.provTabContentVehicles },
      schedules: { btn: elements.btnProvTabSchedules, content: elements.provTabContentSchedules },
      departures: { btn: elements.btnProvTabDepartures, content: elements.provTabContentDepartures },
      hotels: { btn: elements.btnProvTabHotels, content: elements.provTabContentHotels },
      cabs: { btn: elements.btnProvTabCabs, content: elements.provTabContentCabs },
      refunds: { btn: elements.btnProvTabRefunds, content: elements.provTabContentRefunds }
    };
    Object.values(tabMap).forEach(t => {
      if (t && t.btn) t.btn.classList.remove('active');
      if (t && t.content) t.content.classList.add('hidden');
    });
    const selected = tabMap[subtab] || tabMap.vehicles;
    if (selected) {
      if (selected.btn) selected.btn.classList.add('active');
      if (selected.content) selected.content.classList.remove('hidden');
    }
  }

  function switchView(viewName) {
    if (viewName === 'market-search') navigateTo('/search');
    else if (viewName === 'market-bookings') navigateTo('/search');
    else if (viewName === 'directory') navigateTo('/trips');
    else if (viewName === 'provider') navigateTo('/provider');
    else if (viewName === 'admin') navigateTo('/admin');
    else if (viewName === 'workspace') navigateTo(state.currentTripId ? `/trips/${state.currentTripId}` : '/trips');
    else navigateTo('/trips');
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

  async function loadTripById(tripId, subroute = '') {
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}`);
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || `Failed to load trip ${tripId}`);
      }

      state.currentTripId = tripId;
      state.trip = data.data;
      state.disruptionResult = null;

      const targetPath = subroute ? `/trips/${tripId}/${subroute}` : `/trips/${tripId}`;
      if (window.location.pathname !== targetPath) {
        window.history.pushState({}, '', targetPath);
      }

      renderWorkspace(state.trip, subroute);
      showMainSection('workspace');
      startStatusPolling(tripId);
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  function renderWorkspace(trip, subroute = '') {
    if (!trip) return;

    // Check disruption status
    const isDisrupted = trip.status === 'DISRUPTED' && trip.activeDisruptions && trip.activeDisruptions.length > 0;

    // Guard: If accessing /disruption on a healthy trip, redirect to overview
    if (subroute === 'disruption' && !isDisrupted) {
      showToast('Trip is healthy with no active disruptions.', 'info');
      navigateTo(`/trips/${trip.id}`, true);
      return;
    }

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

    // Phase 17: Traveller Needs & Emergency Contact in Overview
    if (elements.overviewTravellerNeeds && elements.tripTravellerNeeds) {
      const needs = [];
      if (trip.hasMobilityAssistance) needs.push('Mobility Assistance');
      if (trip.hasElderlyTraveller) needs.push('Elderly Traveller');
      if (trip.hasTravellingWithChildren) needs.push('Travelling with Children');
      if (trip.hasMedicalNeeds) needs.push('Medical Needs');

      if (needs.length > 0 || (trip.specialNeedsNote && trip.specialNeedsNote.trim())) {
        let text = needs.join(', ');
        if (trip.specialNeedsNote && trip.specialNeedsNote.trim()) {
          text += text ? ` (Note: ${trip.specialNeedsNote})` : `Note: ${trip.specialNeedsNote}`;
        }
        elements.tripTravellerNeeds.textContent = text;
        elements.overviewTravellerNeeds.classList.remove('hidden');
      } else {
        elements.overviewTravellerNeeds.classList.add('hidden');
      }
    }

    if (elements.overviewEmergencyContact && elements.tripEmergencyContact) {
      if (trip.emergencyContactName || trip.emergencyContactPhone) {
        let phoneDisplay = trip.emergencyContactPhone || '';
        if (phoneDisplay.length > 4) {
          phoneDisplay = '+91******' + phoneDisplay.slice(-4);
        }
        const text = `${trip.emergencyContactName || 'Emergency Contact'}${phoneDisplay ? ' (' + phoneDisplay + ')' : ''}`;
        elements.tripEmergencyContact.textContent = text;
        elements.overviewEmergencyContact.classList.remove('hidden');
      } else {
        elements.overviewEmergencyContact.classList.add('hidden');
      }
    }

    // Subnav buttons visibility & active states
    if (elements.tabTripDisruption) {
      elements.tabTripDisruption.classList.toggle('hidden', !isDisrupted);
    }
    const subnavItems = [
      { el: elements.tabTripOverview, active: subroute === '' },
      { el: elements.tabTripDisruption, active: subroute === 'disruption' },
      { el: elements.tabTripRecovery, active: subroute === 'recovery' },
      { el: elements.tabTripPlan, active: subroute === 'plan' },
      { el: elements.tabTripNotifications, active: subroute === 'notifications' },
      { el: elements.tabTripLedger, active: subroute === 'ledger' }
    ];
    subnavItems.forEach(({ el, active }) => {
      if (el) el.classList.toggle('active', active);
    });

    if (elements.linkTripWallet) {
      elements.linkTripWallet.href = `/api/trips/${encodeURIComponent(trip.id)}/wallet`;
    }

    // Toggle Workspace Sections based on subroute
    const isOverview = subroute === '';
    if (elements.sectionTripSummary) elements.sectionTripSummary.classList.remove('hidden');
    if (elements.sectionTimeline) elements.sectionTimeline.classList.toggle('hidden', !isOverview);
    if (elements.sectionLiveStatus) elements.sectionLiveStatus.classList.toggle('hidden', !isOverview);
    if (elements.sectionReadiness) elements.sectionReadiness.classList.toggle('hidden', !isOverview);
    if (elements.sectionMessages) elements.sectionMessages.classList.toggle('hidden', !isOverview);
    if (elements.sectionImport) elements.sectionImport.classList.toggle('hidden', !isOverview);
    if (elements.sectionWallet) elements.sectionWallet.classList.toggle('hidden', !isOverview);
    if (elements.sectionAudit) elements.sectionAudit.classList.toggle('hidden', !isOverview);

    if (elements.sectionDisruption) elements.sectionDisruption.classList.toggle('hidden', subroute !== 'disruption');
    if (elements.sectionRecovery) elements.sectionRecovery.classList.toggle('hidden', subroute !== 'recovery');
    if (elements.sectionPlan) elements.sectionPlan.classList.toggle('hidden', subroute !== 'plan');
    if (elements.sectionNotifications) elements.sectionNotifications.classList.toggle('hidden', subroute !== 'notifications');
    if (elements.sectionLedger) elements.sectionLedger.classList.toggle('hidden', subroute !== 'ledger');

    // BUG FIX: Strictly ensure disruption cascade & output container are hidden on non-disruption view or undisrupted trips
    if (subroute !== 'disruption' || !isDisrupted) {
      if (elements.disruptionOutputContainer) elements.disruptionOutputContainer.classList.add('hidden');
      if (elements.cascadeList) elements.cascadeList.innerHTML = '';
      if (elements.impactSummaryBox) elements.impactSummaryBox.innerHTML = '';
    } else {
      if (elements.disruptionOutputContainer) elements.disruptionOutputContainer.classList.remove('hidden');
    }

    // Auto & Provider-reported disruption banner check
    if (elements.autoDisruptionBanner) {
      const disruptionsToShow = (trip.activeDisruptions || []).filter(
        (d) => d.reason && (d.reason.includes('Detected automatically') || d.reason.includes('Provider-reported'))
      );
      if (disruptionsToShow.length > 0 && isDisrupted) {
        const d = disruptionsToShow[disruptionsToShow.length - 1];
        const bkg = (trip.bookings || []).find((b) => b.id === d.bookingId);
        const bkgDesc = bkg ? `[${bkg.type}] ${bkg.provider}` : (d.bookingId || 'Booking');
        const detail = d.type === 'CANCELLATION' ? 'cancelled' : `delayed by ${d.delayMinutes || 0} min`;
        const isProv = d.reason && d.reason.includes('Provider-reported');
        if (elements.autoDisruptionTitle) {
          elements.autoDisruptionTitle.textContent = isProv
            ? 'Provider-Reported Disruption'
            : 'Disruption Detected Automatically';
        }
        elements.autoDisruptionMessage.textContent = isProv
          ? `Provider-reported, not independently verified: ${bkgDesc} ${detail}`
          : `Disruption detected automatically: ${bkgDesc} ${detail}`;
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
      const transitTypes = new Set(['FLIGHT', 'TRAIN', 'BUS']);
      const transitBookings = (trip.bookings || []).filter((b) => transitTypes.has(String(b.type || '').toUpperCase()));
      const candidates = transitBookings.length > 0 ? transitBookings : (trip.bookings || []);
      elements.selectSimulateBooking.innerHTML = '<option value="">-- Select a booking to simulate --</option>';
      candidates.forEach((b) => {
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
    const isResolved = trip.status === 'RESOLVED';
    const isCompletedBookings = trip.status === 'COMPLETED_BOOKINGS';

    elements.btnFindRecovery.disabled = !isDisrupted;
    elements.btnFindRecovery.title = isDisrupted
      ? 'Find optimal recovery options'
      : 'Recovery options can only be searched when a trip is disrupted.';

    // Show/hide resolved banner and Phase 11 Recovery Progress Tracker
    if (isResolved || isCompletedBookings) {
      elements.sectionResolvedBanner.classList.remove('hidden');
      loadRecoveryActions(trip.id);
    } else {
      elements.sectionResolvedBanner.classList.add('hidden');
      if (elements.sectionRecoveryTracker) {
        elements.sectionRecoveryTracker.classList.add('hidden');
      }
      if (elements.trackerActionsList) {
        elements.trackerActionsList.innerHTML = '';
      }
    }

    // Render Booking Hand-off card
    renderHandoffCard(trip);

    // Fetch ONLY page-relevant data (Client-Side routing optimization)
    if (subroute === 'notifications') {
      loadNotificationHistory(trip.id);
    } else if (subroute === 'ledger') {
      loadLedger(trip.id);
    } else if (isOverview) {
      populateMessageBookingDropdown(trip.bookings || []);
    }

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
    const activePlan = trip.latestPlan || state.planData;
    if (activePlan) {
      state.planData = activePlan;
      if (elements.planEmptyState) elements.planEmptyState.classList.add('hidden');
      if (elements.planContentWrap) elements.planContentWrap.classList.remove('hidden');
      renderPlan(activePlan);
    } else {
      if (elements.planEmptyState) elements.planEmptyState.classList.remove('hidden');
      if (elements.planContentWrap) elements.planContentWrap.classList.add('hidden');
    }

    // Audit Log
    renderAuditLog(trip.auditLog || []);
  }

  // ==========================================
  // Timeline Rendering
  // ==========================================

  function renderTimeline(bookings) {
    renderLiveStatusBookings(bookings);
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

      // Phase 7 & 18: Per-booking status chip
      let chipHtml = '';
      const bState = booking.lastStatusState || 'ON_TIME';
      const bDelay = booking.lastDelayMinutes || 0;
      const bSource = booking.lastStatusSource || 'SIMULATED';
      const isProviderReported = bSource === 'PROVIDER_REPORTED';
      const isLive = bSource === 'LIVE' && state.statusMode === 'LIVE';
      const prefix = isProviderReported
        ? 'Provider-reported: '
        : (isLive ? 'Live: ' : 'Simulated: ');
      const disclaimer = isProviderReported
        ? 'Provider-reported, not independently verified'
        : (isLive ? 'Live carrier data' : 'Simulated data, not a real tracking source');
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

      const operatorChip = booking.operator
        ? `<span class="badge" style="background: rgba(56, 139, 253, 0.15); color: #58a6ff; border: 1px solid rgba(56, 139, 253, 0.35); font-size: 0.75rem; padding: 2px 8px; border-radius: 9999px;">${escapeHtml(booking.operator)}</span>`
        : '';
      const vehicleChip = booking.vehicleNumber
        ? `<span class="badge" style="background: rgba(163, 113, 247, 0.15); color: #bc8cff; border: 1px solid rgba(163, 113, 247, 0.35); font-size: 0.75rem; padding: 2px 8px; border-radius: 9999px;">${escapeHtml(booking.vehicleNumber)}</span>`
        : '';

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
              ${operatorChip}
              ${vehicleChip}
              ${chipHtml}
              ${isRebooked ? '<span class="badge badge-blue">REBOOKED</span>' : ''}
              ${isEstimated ? '<span class="badge badge-warning badge-estimated">ESTIMATED</span>' : ''}
              ${isProposed ? '<span class="badge badge-purple">Proposed</span>' : ''}
              ${booking.providerStatus === 'SUSPENDED' ? '<span class="badge badge-danger">Provider suspended</span>' : ''}
              ${booking.cannotHonour ? '<span class="badge badge-warning" title="Provider reported cannot honour">⚠️ Hotel cannot honour</span>' : ''}
            </div>
            <div class="booking-header-actions">
              <span class="badge ${statusClass}">${escapeHtml(formatStatus(booking.status))}</span>
              ${!isCancelled ? `<button type="button" class="btn-cancel-booking-action" data-booking-id="${escapeHtml(booking.id)}" style="color: #ef4444; background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.35); border-radius: 4px; padding: 2px 8px; font-size: 0.75rem; cursor: pointer; margin-right: 4px;" title="Cancel this booking">Cancel Booking</button>` : ''}
              ${booking.departureId && state.statusMode !== 'LIVE' ? `
                <button type="button" class="btn-icon-sm btn-simulate-departure" data-departure-id="${escapeHtml(booking.departureId)}" data-operator="${escapeHtml(booking.operator || booking.provider)}" data-vehicle="${escapeHtml(booking.vehicleNumber || '')}" data-status="${escapeHtml(booking.lastStatusState || 'SCHEDULED')}" data-delay="${booking.lastDelayMinutes || 0}" title="Simulate carrier status for this departure">
                  Simulate
                </button>
              ` : ''}
              <button type="button" class="btn-report-problem" data-booking-id="${escapeHtml(booking.id)}" title="Report a problem for this booking">Report Problem</button>
              <button class="btn-icon-sm btn-edit-booking" data-booking-id="${escapeHtml(booking.id)}" title="Edit booking">Edit</button>
              <button class="btn-icon-sm danger btn-delete-booking" data-booking-id="${escapeHtml(booking.id)}" title="Delete booking">&times;</button>
            </div>
          </div>
          <div class="timeline-card-body">
            ${booking.cannotHonour ? `
              <div class="banner banner-warning" style="margin-bottom: 0.75rem; font-size: 0.8rem; background: rgba(234, 179, 8, 0.15); border: 1px solid #eab308; color: #fef08a; padding: 0.4rem 0.6rem; border-radius: 4px;">
                <strong>Provider Reported:</strong> Hotel reported it cannot honour this booking: "${escapeHtml(booking.cannotHonourReason || '')}".
              </div>
            ` : ''}
            ${booking.providerStatus === 'SUSPENDED' ? `
              <div class="banner banner-danger" style="margin-bottom: 0.75rem; font-size: 0.8rem; background: rgba(239, 68, 68, 0.15); border: 1px solid #ef4444; color: #fca5a5; padding: 0.4rem 0.6rem; border-radius: 4px;">
                <strong>Provider Suspended:</strong> This provider has been suspended by Odyssey administration.
              </div>
            ` : ''}
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

    // Attach cancel / edit / delete / report listeners
    container.querySelectorAll('.btn-cancel-booking-action').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const bkgId = e.currentTarget.getAttribute('data-booking-id');
        promptCancelBooking(bkgId);
      });
    });

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

  // Render Monitored Bookings in Transit Status & Monitoring section
  function renderLiveStatusBookings(bookings) {
    const tbody = elements.liveStatusBookingsTbody;
    if (!tbody) return;

    if (!bookings || bookings.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty-state">No booked transit or lodging loaded yet.</td></tr>';
      return;
    }

    tbody.innerHTML = '';
    bookings.forEach(b => {
      const tr = document.createElement('tr');
      const bType = (b.type || 'UNKNOWN').toUpperCase();
      let typeBadgeClass = 'badge-neutral';
      if (bType === 'BUS') typeBadgeClass = 'badge-amber';
      else if (bType === 'TRAIN') typeBadgeClass = 'badge-blue';
      else if (bType === 'HOTEL') typeBadgeClass = 'badge-gold';
      else if (bType === 'CAB') typeBadgeClass = 'badge-cyan';
      else if (bType === 'FLIGHT') typeBadgeClass = 'badge-purple';

      const providerName = escapeHtml(b.provider || b.operator || '-');
      const vehicleNum = b.vehicleNumber ? `<div style="font-size: 0.75rem; color: #bc8cff; font-family: monospace;">${escapeHtml(b.vehicleNumber)}</div>` : '';
      const routeText = (b.from && b.to)
        ? `${escapeHtml(toTitleCase(b.from))} &rarr; ${escapeHtml(toTitleCase(b.to))}`
        : escapeHtml(b.from || b.to || '-');
      const scheduleText = b.startTime ? `<div style="font-size: 0.75rem; color: var(--text-muted);">${formatDateTime(b.startTime)}</div>` : '';

      // Transit Status Chip
      let chipHtml = '';
      const bState = b.lastStatusState || 'ON_TIME';
      const bDelay = b.lastDelayMinutes || 0;
      const bSource = b.lastStatusSource || 'SIMULATED';
      const isProviderReported = bSource === 'PROVIDER_REPORTED';
      const isLive = bSource === 'LIVE' && state.statusMode === 'LIVE';
      const prefix = isProviderReported ? 'Provider: ' : (isLive ? 'Live: ' : 'Simulated: ');

      if (b.status === 'CANCELLED' || bState === 'CANCELLED') {
        chipHtml = `<span class="status-chip chip-cancelled"><span class="chip-dot"></span> ${prefix}Cancelled</span>`;
      } else if (bState === 'DELAYED') {
        chipHtml = `<span class="status-chip chip-delayed"><span class="chip-dot"></span> ${prefix}Delayed ${bDelay}m</span>`;
      } else if (bState === 'ON_TIME') {
        chipHtml = `<span class="status-chip chip-on-time"><span class="chip-dot"></span> ${prefix}On time</span>`;
      } else {
        chipHtml = `<span class="status-chip chip-unavailable"><span class="chip-dot"></span> ${prefix}Status unavail</span>`;
      }

      const isCancelled = b.status === 'CANCELLED';
      const actionHtml = isCancelled
        ? '<span class="badge badge-cancelled" style="font-size: 0.75rem; padding: 3px 8px;">Cancelled</span>'
        : `<button type="button" class="btn btn-outline btn-sm btn-cancel-booking-action" data-booking-id="${escapeHtml(b.id)}" style="color: #ef4444; border-color: rgba(239, 68, 68, 0.4); background: rgba(239, 68, 68, 0.08); padding: 3px 8px; font-size: 0.75rem; cursor: pointer;">Cancel Booking</button>`;

      tr.innerHTML = `
        <td><span class="badge ${typeBadgeClass}">${escapeHtml(bType)}</span></td>
        <td><strong>${providerName}</strong>${vehicleNum}</td>
        <td><div>${routeText}</div>${scheduleText}</td>
        <td>${chipHtml}</td>
        <td>${actionHtml}</td>
      `;
      tbody.appendChild(tr);
    });

    tbody.querySelectorAll('.btn-cancel-booking-action').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const bkgId = e.currentTarget.getAttribute('data-booking-id');
        promptCancelBooking(bkgId);
      });
    });
  }

  // Unified Booking Cancellation (Bus, Hotel, Cab, etc.)
  async function promptCancelBooking(bookingId) {
    if (!state.trip || !state.trip.bookings) return;
    const booking = state.trip.bookings.find(b => b.id === bookingId);
    if (!booking) return;

    const bType = (booking.type || '').toUpperCase();
    const bTitle = `[${booking.type || 'Booking'}] ${booking.provider || ''}`;

    const marketBooking = (state.myMarketBookings || (typeof marketState !== 'undefined' && marketState.myBookings) || []).find(mb =>
      mb.id === booking.id ||
      (mb.bookingReference && mb.bookingReference === booking.bookingReference) ||
      (mb.departureId && mb.departureId === booking.departureId)
    );

    if (marketBooking && typeof openCancelBookingModal === 'function') {
      openCancelBookingModal(marketBooking.id, marketBooking.bookingReference || booking.bookingReference || booking.id);
      return;
    }

    let policyNote = '';
    if (bType === 'HOTEL') {
      policyNote = '\n\nNote: Hotel bookings are strictly non-refundable once confirmed.';
    } else if (bType === 'CAB') {
      policyNote = '\n\nNote: Cabs do not auto-refund upon cancellation.';
    } else {
      policyNote = '\n\nNote: Tiered refund policy applies.';
    }

    const confirmed = confirm(`Are you sure you want to cancel this ${booking.type || 'service'} booking?\n\n${bTitle}\n${booking.from || ''} -> ${booking.to || ''}${policyNote}`);
    if (!confirmed) return;

    try {
      if (booking.bookingReference) {
        try {
          await fetch(`/api/market/bookings/${encodeURIComponent(booking.bookingReference)}/cancel`, { method: 'POST' });
        } catch (_) {}
      }

      const res = await fetch(`/api/trips/${encodeURIComponent(state.currentTripId)}/bookings/${encodeURIComponent(booking.id)}/report-problem`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'CANCELLED', delayMinutes: 0, note: `Cancelled by traveller (${booking.type})` })
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to cancel booking');
      }

      showToast(`Successfully cancelled ${bType} booking (${bTitle})!`, 'success');
      await loadTripById(state.currentTripId);
      if (typeof loadMyMarketBookings === 'function') {
        await loadMyMarketBookings();
      }
    } catch (err) {
      showToast(err.message, 'danger');
    }
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
    const btnTop = elements.btnCheckStatusTop;
    if (btn) setButtonLoading(btn, true);
    if (btnTop) setButtonLoading(btnTop, true);
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
      } else {
        if (elements.statusWindowNote) {
          elements.statusWindowNote.classList.add('hidden');
        }
      }

      // Reload trip to refresh timeline chips, disruptions, banner, and audit entries
      await loadTripById(state.currentTripId);

    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      if (btn) setButtonLoading(btn, false);
      if (btnTop) setButtonLoading(btnTop, false);
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
        const isProvider = opt.source === 'PROVIDER' || (mainLeg.source === 'PROVIDER');
        const isMarketplace = !isProvider && (opt.source === 'MARKETPLACE' || (mainLeg.source === 'MARKETPLACE') || !!mainLeg.departureId);
        const operatorName = mainLeg.operator || opt.operator || '';
        const vehicleNum = mainLeg.vehicleNumber || opt.vehicleNumber || '';
        const seatsLeft = mainLeg.seatsAvailable != null ? mainLeg.seatsAvailable : (opt.seatsAvailable != null ? opt.seatsAvailable : null);
        const schedLabel = mainLeg.scheduleLabel || opt.scheduleLabel || '';

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
              ${isProvider ? '<span class="badge badge-provider" style="font-size: 0.72rem;">Provider-listed &bull; ' + escapeHtml(operatorName || provider) + '</span>' : (isMarketplace ? '<span class="badge badge-green" style="font-size: 0.72rem;">Bookable in demo marketplace</span>' : (isEstimated ? '<span class="badge badge-warning badge-estimated">ESTIMATED</span>' : '<span class="badge badge-info">INVENTORY</span>'))}
              <span class="option-score">Score: ${opt.score}</span>
            </div>
          </div>
          <div class="option-body">
            ${opt.bestAvailableNote ? `
              <div class="option-suggested-tag" style="background: rgba(16, 185, 129, 0.15); border: 1px solid #10b981; color: #34d399; padding: 4px 10px; border-radius: 4px; font-size: 0.78rem; font-weight: 600; margin-bottom: 0.5rem; display: inline-flex; align-items: center; gap: 4px;">
                <span>✨</span> <span>${escapeHtml(opt.bestAvailableNote)}</span>
              </div>
            ` : (isBestAvailable ? `
              <div class="option-broken-badge">${escapeHtml(bestAvailableText)}</div>
            ` : '')}
            ${isEstimated ? `
              <div class="estimated-notice">
                <span class="badge badge-warning">ESTIMATED</span>
                <span class="estimated-note">Note: Estimated options are not real timetables.</span>
              </div>
            ` : ''}
            ${isProvider ? `
              <div class="schedule-disclaimer-notice" style="margin-bottom: 0.75rem; font-size: 0.8rem;">
                ⚠️ <strong>Provider-listed:</strong> Odyssey does not verify schedules or availability.
              </div>
            ` : ''}
            <div class="option-route-info">
              <span class="option-provider">${escapeHtml(provider)}${operatorName && operatorName !== provider ? ' (' + escapeHtml(operatorName) + ')' : ''} ${vehicleNum ? '<span class="badge badge-neutral">' + escapeHtml(vehicleNum) + '</span>' : ''}</span>
              ${seatsLeft != null ? `<span class="badge ${seatsLeft > 5 ? 'badge-neutral' : 'badge-amber'}" style="font-size: 0.75rem;">${seatsLeft} seat${seatsLeft === 1 ? '' : 's'} left</span>` : ''}
              ${schedLabel ? `<span class="badge badge-warning" style="font-size: 0.72rem;">${escapeHtml(schedLabel)}</span>` : ''}
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
      if (state.trip) {
        state.trip.latestPlan = data.data;
      }
      loadNotificationHistory(tripId);
      showToast(`Composed recovery plan for ${resolvedOptionId}`, 'info');

      // Navigate cleanly to the dedicated recovery plan route (never show recovery and plan together)
      navigateTo(`/trips/${encodeURIComponent(tripId)}/plan`);
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      if (button) setButtonLoading(button, false);
    }
  }

  function renderPlan(planData) {
    if (!planData) {
      if (elements.planEmptyState) elements.planEmptyState.classList.remove('hidden');
      if (elements.planContentWrap) elements.planContentWrap.classList.add('hidden');
      return;
    }
    state.currentPlan = planData;

    if (elements.planEmptyState) elements.planEmptyState.classList.add('hidden');
    if (elements.planContentWrap) elements.planContentWrap.classList.remove('hidden');

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
      const timingHtml = line.timingNote
        ? `<div class="cost-line-timing-note" style="font-size: 0.75rem; color: #94a3b8; margin-top: 2px;">ℹ ${escapeHtml(line.timingNote)}</div>`
        : '';
      row.innerHTML = `
        <div class="cost-line-info">
          <span class="cost-line-label">${escapeHtml(line.label)}</span>
          <span class="cost-line-note">${escapeHtml(line.assumptionNote || '')}</span>
          ${timingHtml}
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

    // Phase 10C: Book Replacement Leg Banner
    const repLeg = (planData.newLeg && (planData.newLeg.source === 'MARKETPLACE' || planData.newLeg.departureId))
      ? planData.newLeg
      : (state.trip && state.trip.bookings
        ? state.trip.bookings.find(b => (b.status === 'PROPOSED' || b.status === 'REBOOKED') && (b.source === 'MARKETPLACE' || b.departureId))
        : null);

    let replacementCard = document.getElementById('plan-marketplace-replacement-card');
    if (!replacementCard && elements.diffTableBody && elements.diffTableBody.parentElement) {
      replacementCard = document.createElement('div');
      replacementCard.id = 'plan-marketplace-replacement-card';
      elements.diffTableBody.parentElement.parentElement.insertBefore(replacementCard, elements.diffTableBody.parentElement.nextSibling);
    }

    if (replacementCard) {
      if (repLeg && repLeg.departureId && state.trip && state.trip.id) {
        const isAlreadyConfirmed = state.trip.bookings && state.trip.bookings.some(b => b.status === 'CONFIRMED' && b.departureId === repLeg.departureId);
        if (!isAlreadyConfirmed) {
          let oldDisruptedBookingId = '';
          if (state.trip.activeDisruptions && state.trip.activeDisruptions.length > 0) {
            oldDisruptedBookingId = state.trip.activeDisruptions[0].bookingId || '';
          }
          if (!oldDisruptedBookingId && state.trip.bookings) {
            const oldB = state.trip.bookings.find(b => (b.status === 'CANCELLED' || b.status === 'DELAYED' || b.status === 'AT_RISK'));
            if (oldB) oldDisruptedBookingId = oldB.id;
          }

          replacementCard.innerHTML = `
            <div style="background: rgba(35, 134, 54, 0.15); border: 1px solid rgba(46, 160, 67, 0.4); border-radius: 8px; padding: 14px; margin: 16px 0; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px;">
              <div>
                <span class="badge badge-green" style="margin-bottom: 4px;">DEMO MARKETPLACE REPLACEMENT</span>
                <div style="font-weight: 600; font-size: 1rem; color: #f0f6fc;">${escapeHtml(repLeg.operator || repLeg.provider)} (${escapeHtml(repLeg.vehicleNumber || 'Transit')})</div>
                <div style="font-size: 0.85rem; color: #8b949e;">${escapeHtml(repLeg.from)} &rarr; ${escapeHtml(repLeg.to)} &bull; Departure: ${formatDateTime(repLeg.startTime)}</div>
              </div>
              <div>
                <button type="button" class="btn btn-gold btn-book-replacement-trigger" data-trip-id="${escapeHtml(state.trip.id)}" data-departure-id="${escapeHtml(repLeg.departureId)}" data-old-booking-id="${escapeHtml(oldDisruptedBookingId)}">
                  Book this replacement in the demo marketplace &rarr;
                </button>
              </div>
            </div>
          `;
          replacementCard.classList.remove('hidden');

          const btnTrigger = replacementCard.querySelector('.btn-book-replacement-trigger');
          if (btnTrigger) {
            btnTrigger.addEventListener('click', () => {
              openReplacementBookingModal(
                btnTrigger.getAttribute('data-trip-id'),
                btnTrigger.getAttribute('data-old-booking-id'),
                btnTrigger.getAttribute('data-departure-id')
              );
            });
          }
        } else {
          replacementCard.classList.add('hidden');
          replacementCard.innerHTML = '';
        }
      } else {
        replacementCard.classList.add('hidden');
        replacementCard.innerHTML = '';
      }
    }

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

        if (e.target.checked) {
          const isRefund = item.type === 'REQUEST_REFUND'
            || (item.id && String(item.id).toUpperCase().includes('REFUND'))
            || (item.text && item.text.toLowerCase().includes('refund'));

          if (isRefund) {
            let note = item.timingNote;
            if (!note && planData && planData.costBreakdown && Array.isArray(planData.costBreakdown.lines)) {
              const refLine = planData.costBreakdown.lines.find(l => l.timingNote);
              if (refLine) note = refLine.timingNote;
            }
            if (!note) {
              const isDemo = (state.trip && state.trip.id && state.trip.id.toUpperCase().includes('DEMO')) || (item.text && item.text.includes('Demo'));
              note = isDemo
                ? "Demo refund. No real money is moved. Demo marketplace. No real ticket is issued and no money was charged."
                : "Refunds are not instant. They usually take a few working days (typically 5 to 7). This is an estimate, not a guarantee, and depends on the operator and the payment method. Odyssey cannot confirm when a refund is received.";
            }

            window.alert(`Refund Timing Notice:\n\n${note}`);
            showToast(`Refund Timing Notice: ${note}`, 'info');
          }
        }
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

    // Phase 12 Add-on: Approve confirmation dialog whenever plan contains a refund
    if (state.currentPlan && state.currentPlan.costBreakdown && Array.isArray(state.currentPlan.costBreakdown.lines)) {
      const refundLine = state.currentPlan.costBreakdown.lines.find(l => l.isDeduction || (l.label && l.label.toLowerCase().includes('refund')));
      if (refundLine) {
        const note = refundLine.timingNote || "Refunds are not instant. They usually take a few working days (typically 5 to 7). This is an estimate, not a guarantee, and depends on the operator and the payment method. Odyssey cannot confirm when a refund is received.";
        const confirmed = window.confirm(`Refund Timing Notice:\n\n${note}\n\nDo you want to proceed with approving this recovery plan?`);
        if (!confirmed) {
          return;
        }
      }
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

      // Navigate back to overview route showing updated RESOLVED status immediately
      navigateTo(`/trips/${encodeURIComponent(tripId)}`);
      if (elements.sectionResolvedBanner) {
        elements.sectionResolvedBanner.scrollIntoView({ behavior: 'smooth' });
      }
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

      state.planData = null;
      if (state.trip) state.trip.latestPlan = null;
      showToast('Recovery plan rejected. Trip remains in DISRUPTED status.', 'warning');

      // Navigate back to overview route showing updated DISRUPTED status immediately
      navigateTo(`/trips/${encodeURIComponent(tripId)}`);
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnRejectPlan, false);
    }
  }

  // ==========================================
  // Phase 11: Recovery Execution Tracker
  // ==========================================

  async function loadRecoveryActions(tripId) {
    if (!elements.sectionRecoveryTracker || !tripId) return;
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/actions`);
      if (!res.ok) {
        const errPayload = await res.json().catch(() => ({}));
        const msg = errPayload.message || (res.status === 403 ? 'Access denied: Recovery actions are visible only to the trip owner.' : `Failed to load recovery actions (HTTP ${res.status})`);
        renderRecoveryTrackerError(msg);
        return;
      }
      const payload = await res.json();
      if (payload.success && payload.data && payload.data.approved) {
        renderRecoveryTracker(payload.data);
      } else {
        renderRecoveryTrackerError((payload && payload.message) || 'No recovery action data available.');
      }
    } catch (err) {
      console.warn('Failed to load recovery actions:', err);
      renderRecoveryTrackerError(`Unable to load recovery actions: ${escapeHtml(err.message)}`);
    }
  }

  function renderRecoveryTrackerError(message) {
    if (!elements.sectionRecoveryTracker) return;
    elements.sectionRecoveryTracker.classList.remove('hidden');
    if (elements.trackerActionsList) {
      elements.trackerActionsList.innerHTML = `<p class="empty-state" style="color: #f87171;">${escapeHtml(message)}</p>`;
    }
    if (elements.trackerProgressBadge) {
      elements.trackerProgressBadge.textContent = 'Unavailable';
      elements.trackerProgressBadge.className = 'badge badge-neutral';
    }
  }

  function renderRecoveryTracker(trackerData) {
    if (!elements.sectionRecoveryTracker || !elements.trackerActionsList) return;
    elements.sectionRecoveryTracker.classList.remove('hidden');

    const total = trackerData.totalCount || 0;
    const done = trackerData.doneCount || 0;
    const pct = total > 0 ? Math.round((done / total) * 100) : 0;

    if (elements.trackerProgressBadge) {
      elements.trackerProgressBadge.textContent = trackerData.progressText || `${done} of ${total} done`;
      elements.trackerProgressBadge.className = `badge ${total > 0 && done === total ? 'badge-success' : 'badge-info'}`;
    }
    if (elements.trackerProgressBarFill) {
      elements.trackerProgressBarFill.style.width = `${pct}%`;
    }

    const actions = trackerData.actions || [];
    const hasBlockedCleanup = actions.some((a) => !a.canComplete && a.status === 'PENDING');
    if (elements.trackerOrderNotice) {
      elements.trackerOrderNotice.classList.toggle('hidden', !hasBlockedCleanup);
    }

    if (actions.length === 0) {
      elements.trackerActionsList.innerHTML = '<p class="empty-state">No tracked recovery actions for this plan.</p>';
      return;
    }

    elements.trackerActionsList.innerHTML = actions.map((action) => {
      const status = action.status || 'PENDING';
      const badgeClass = status === 'DONE' ? 'badge-success' : (status === 'SKIPPED' ? 'badge-neutral' : 'badge-warning');
      const isPending = status === 'PENDING';
      const disabledAttr = !action.canComplete ? 'disabled' : '';
      const metaParts = [];
      if (action.bookingReference) {
        metaParts.push(`<span class="tracker-meta-chip">Ref: ${escapeHtml(action.bookingReference)}</span>`);
      } else if (action.relatedBookingId) {
        metaParts.push(`<span class="tracker-meta-chip">Booking: ${escapeHtml(action.relatedBookingId)}</span>`);
      }
      if (action.contactInfo) {
        metaParts.push(`<span class="tracker-meta-chip">Contact: ${escapeHtml(action.contactInfo)}</span>`);
      }
      if (action.completedAt) {
        metaParts.push(`<span class="tracker-meta-chip">Completed: ${escapeHtml(String(action.completedAt).replace('T', ' ').slice(0, 16))}</span>`);
      }
      const metaHtml = metaParts.length > 0 ? `<div class="tracker-action-meta">${metaParts.join('')}</div>` : '';
      const noteHtml = action.note ? `<div class="tracker-action-note">Note: ${escapeHtml(action.note)}</div>` : '';
      const blockedHtml = (!action.canComplete && isPending && action.disabledReason)
        ? `<div class="tracker-blocked-reason">${escapeHtml(action.disabledReason)}</div>`
        : '';

      let draftHotelBtn = '';
      if (action.type === 'MESSAGE_HOTEL') {
        draftHotelBtn = `<button type="button" class="btn btn-sm btn-outline btn-tracker-draft-hotel" data-booking-id="${escapeHtml(action.relatedBookingId || '')}" style="color: var(--gold-light); border-color: var(--gold-primary);">Draft hotel message</button>`;
      }

      const isRefundAction = action.type === 'REQUEST_REFUND'
        || (action.id && String(action.id).toUpperCase().includes('REFUND'))
        || (action.text && action.text.toLowerCase().includes('refund'));
      const ledgerCtaBtn = isRefundAction
        ? `<button type="button" class="btn btn-sm btn-gold btn-goto-ledger-action" style="margin-left: 0.25rem;" title="Open Costs and Refunds page to view or record refunds">Costs &amp; Refunds &rarr;</button>`
        : '';

      const buttonsHtml = isPending
        ? `<button type="button" class="btn btn-sm btn-primary btn-tracker-action" data-action-id="${escapeHtml(action.id)}" data-verb="complete" ${disabledAttr}>Complete</button>
           <button type="button" class="btn btn-sm btn-secondary btn-tracker-action" data-action-id="${escapeHtml(action.id)}" data-verb="skip">Skip</button>
           ${draftHotelBtn}
           ${ledgerCtaBtn}`
        : `<button type="button" class="btn btn-sm btn-secondary btn-tracker-action" data-action-id="${escapeHtml(action.id)}" data-verb="reopen">Reopen</button>
           ${draftHotelBtn}
           ${ledgerCtaBtn}`;

      return `
        <div class="tracker-action-item status-${escapeHtml(status.toLowerCase())}" data-action-id="${escapeHtml(action.id)}">
          <div class="tracker-action-header">
            <div class="tracker-action-title-group">
              <span class="badge ${badgeClass}">${escapeHtml(status)}</span>
              <span class="tracker-action-type">${escapeHtml(action.type || '')}</span>
            </div>
          </div>
          <div class="tracker-action-text">${escapeHtml(action.text || '')}</div>
          ${metaHtml}
          ${noteHtml}
          ${blockedHtml}
          <div class="tracker-action-controls">
            <input type="text" class="tracker-note-input" placeholder="${isPending ? 'Optional note (required to skip)' : 'Optional note to reopen'}" value="${escapeHtml(action.note || '')}">
            <div class="tracker-action-buttons">${buttonsHtml}</div>
          </div>
        </div>
      `;
    }).join('');
  }

  async function handleRecoveryActionMutation(actionId, verb, note, triggerBtn) {
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    if (!tripId || !actionId) return;

    setButtonLoading(triggerBtn, true);
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/actions/${encodeURIComponent(actionId)}/${verb}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ note: note || null }),
      });
      const payload = await res.json();
      if (!res.ok || !payload.success) {
        throw new Error(payload.message || `Failed to ${verb} recovery action`);
      }

      renderRecoveryTracker(payload.data);
      const toastLabel = verb === 'complete' ? 'Action marked DONE.' : (verb === 'skip' ? 'Action marked SKIPPED.' : 'Action reopened to PENDING.');
      showToast(toastLabel, 'success');

      if (verb === 'complete' && actionId && String(actionId).toUpperCase().includes('REFUND')) {
        let note = "Refunds are not instant. They usually take a few working days (typically 5 to 7). This is an estimate, not a guarantee, and depends on the operator and the payment method. Odyssey cannot confirm when a refund is received.";
        if (state.trip && state.trip.id && state.trip.id.toUpperCase().includes('DEMO')) {
          note = "Demo refund. No real money is moved. Demo marketplace. No real ticket is issued and no money was charged.";
        }
        window.alert(`Refund Timing Notice:\n\n${note}`);
      }
      await refreshTripAuditOnly(tripId);
    } catch (err) {
      showToast(err.message, 'danger');
      await loadRecoveryActions(tripId);
    } finally {
      setButtonLoading(triggerBtn, false);
    }
  }

  async function refreshTripAuditOnly(tripId) {
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}`);
      if (!res.ok) return;
      const payload = await res.json();
      if (payload.success && payload.data) {
        state.trip = payload.data;
        renderAuditLog(payload.data.auditLog || []);
      }
    } catch (err) {
      console.warn('Could not refresh audit log:', err);
    }
  }

  // ==========================================
  // Phase 12: Cost and Refund Ledger
  // ==========================================

  async function loadLedger(tripId) {
    if (!elements.sectionLedger || !tripId) return;
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/ledger`);
      if (!res.ok) {
        const errPayload = await res.json().catch(() => ({}));
        const msg = errPayload.message || (res.status === 403 ? 'Access denied: Only the trip owner can view costs and refunds.' : `Failed to load ledger (HTTP ${res.status})`);
        renderLedgerError(msg);
        return;
      }
      const payload = await res.json();
      if (payload.success && payload.data) {
        renderLedger(payload.data);
      } else {
        renderLedgerError((payload && payload.message) || 'No ledger data returned from server.');
      }
    } catch (err) {
      console.warn('Failed to load cost and refund ledger:', err);
      renderLedgerError(`Unable to load cost and refund ledger: ${escapeHtml(err.message)}`);
    }
  }

  function renderLedgerError(message) {
    if (!elements.sectionLedger) return;
    elements.sectionLedger.classList.remove('hidden');
    if (elements.ledgerTableBody) {
      elements.ledgerTableBody.innerHTML = `<tr><td colspan="8" class="empty-state" style="color: #f87171;">${escapeHtml(message)}</td></tr>`;
    }
  }

  function renderLedger(ledgerData) {
    if (!elements.sectionLedger) return;
    elements.sectionLedger.classList.remove('hidden');

    const sum = ledgerData.summary || {};
    if (elements.ledgerSumOriginal) elements.ledgerSumOriginal.textContent = `Rs ${(sum.originalCost !== undefined ? Number(sum.originalCost) : 0).toFixed(2)}`;
    if (elements.ledgerSumReplacement) elements.ledgerSumReplacement.textContent = `Rs ${(sum.replacementCost !== undefined ? Number(sum.replacementCost) : 0).toFixed(2)}`;
    if (elements.ledgerSumExtra) elements.ledgerSumExtra.textContent = `Rs ${(sum.estimatedExtraCost !== undefined ? Number(sum.estimatedExtraCost) : 0).toFixed(2)}`;
    if (elements.ledgerSumExpectedRefunds) elements.ledgerSumExpectedRefunds.textContent = `Rs ${(sum.expectedRefunds !== undefined ? Number(sum.expectedRefunds) : 0).toFixed(2)}`;
    if (elements.ledgerSumRecordedRefunds) elements.ledgerSumRecordedRefunds.textContent = `Rs ${(sum.recordedRefunds !== undefined ? Number(sum.recordedRefunds) : 0).toFixed(2)}`;
    if (elements.ledgerSumOutstandingRefunds) elements.ledgerSumOutstandingRefunds.textContent = `Rs ${(sum.outstandingRefunds !== undefined ? Number(sum.outstandingRefunds) : 0).toFixed(2)}`;
    const netVal = sum.netTotal !== undefined ? sum.netTotal : (ledgerData.netTotal !== undefined ? ledgerData.netTotal : 0);
    if (elements.ledgerSumNet) elements.ledgerSumNet.textContent = `Rs ${Number(netVal).toFixed(2)}`;

    // Phase 12 Add-on: Refund timing note
    const timingNoteText = ledgerData.timingNote || sum.timingNote;
    if (elements.ledgerTimingNote && timingNoteText) {
      elements.ledgerTimingNote.textContent = timingNoteText;
    }

    // Phase 12 Add-on: Booking Refund Breakdown
    if (elements.ledgerBookingSummariesWrap && elements.ledgerBookingSummariesBody) {
      const bSummaries = ledgerData.bookingRefunds || [];
      if (bSummaries.length > 0) {
        elements.ledgerBookingSummariesWrap.classList.remove('hidden');
        elements.ledgerBookingSummariesBody.innerHTML = bSummaries.map((b) => {
          const bookingName = b.provider ? `${escapeHtml(b.provider)} (${escapeHtml(b.type || '')})` : `Booking ${escapeHtml(b.bookingId || '')}`;
          const deductionsHtml = (b.deductions && b.deductions.length > 0)
            ? b.deductions.map(d => `<div style="font-size: 0.8rem; margin-top: 2px;"><span class="badge badge-neutral">${escapeHtml(d.category || '')}</span> Rs ${Number(d.amount || 0).toFixed(2)}: ${escapeHtml(d.description || '')}</div>`).join('')
            : '—';
          const statusClass = b.status === 'RECEIVED' ? 'badge-basis-user-reported'
            : (b.status === 'EXPECTED' ? 'badge-basis-estimated' : 'badge-neutral');
          const reductionVal = Number(b.reduction || b.totalDeductions || 0);
          const reductionHtml = reductionVal > 0
            ? `<span class="badge badge-warning" style="white-space: normal;">Reduction: Rs ${reductionVal.toFixed(2)} (explained by deductions)</span>`
            : 'Rs 0.00';
          const outstandingVal = Number(b.outstandingAmount || 0);
          const outstandingHtml = outstandingVal > 0
            ? `<span class="text-amber font-bold">Rs ${outstandingVal.toFixed(2)}</span>`
            : `<span class="text-emerald">Rs 0.00</span>`;
          return `
            <tr>
              <td><strong>${bookingName}</strong><br><small class="text-muted">Ref: ${escapeHtml(b.bookingReference || '—')}</small></td>
              <td>Rs ${Number(b.originalCost || 0).toFixed(2)}</td>
              <td class="text-emerald font-bold">Rs ${Number(b.grossRefund || 0).toFixed(2)}</td>
              <td>${deductionsHtml}</td>
              <td class="text-emerald font-bold">Rs ${Number(b.netRefund || 0).toFixed(2)}</td>
              <td>${reductionHtml}</td>
              <td><span class="badge ${statusClass}">${escapeHtml(b.status || '')}</span></td>
              <td>${outstandingHtml}</td>
            </tr>
          `;
        }).join('');
      } else {
        elements.ledgerBookingSummariesWrap.classList.add('hidden');
      }
    }

    // Populate Booking dropdown in Record Refund form
    if (elements.ledgerRefundBookingSelect && state.trip && state.trip.bookings) {
      const currentSelected = elements.ledgerRefundBookingSelect.value;
      const optionsHtml = ['<option value="">Select booking...</option>'];
      state.trip.bookings.forEach((b) => {
        const costStr = b.cost !== undefined ? `Rs ${Number(b.cost).toFixed(2)}` : '';
        const statusStr = b.status || '';
        const label = `${escapeHtml(b.provider || b.id)} (${escapeHtml(b.type || '')} - ${costStr} - ${statusStr})`;
        optionsHtml.push(`<option value="${escapeHtml(b.id)}">${label}</option>`);
      });
      elements.ledgerRefundBookingSelect.innerHTML = optionsHtml.join('');
      if (currentSelected) {
        elements.ledgerRefundBookingSelect.value = currentSelected;
      }
    }

    // Set today's date on date input if empty
    if (elements.ledgerRefundDateInput && !elements.ledgerRefundDateInput.value) {
      elements.ledgerRefundDateInput.value = new Date().toISOString().slice(0, 10);
    }

    updateLiveRefundPreview();

    // Populate Table rows (8 columns)
    const tbody = elements.ledgerTableBody;
    if (!tbody) return;

    const entries = ledgerData.entries || [];
    if (entries.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="empty-state">No ledger entries recorded yet.</td></tr>';
      return;
    }

    tbody.innerHTML = entries.map((e) => {
      const basis = e.basis || 'RECORDED';
      const basisClass = basis === 'RECORDED' ? 'badge-basis-recorded'
        : (basis === 'ESTIMATED' ? 'badge-basis-estimated'
          : (basis === 'DEMO' ? 'badge-basis-demo' : 'badge-basis-user-reported'));

      const isDeduction = e.deduction || e.type === 'REFUND_EXPECTED' || e.type === 'REFUND_RECORDED' || e.type === 'REFUND' || e.type === 'REFUND_DEDUCTION';
      const amountPrefix = isDeduction ? '-Rs ' : 'Rs ';
      const amountClass = isDeduction ? 'text-emerald font-bold' : 'font-bold';
      const amountStr = `${amountPrefix}${Number(e.amount || 0).toFixed(2)}`;

      const bookingLabel = e.bookingDescription || (e.bookingId ? `Booking ${e.bookingId}` : '—');
      const catDesc = (e.category || e.description)
        ? `<span class="badge badge-neutral">${escapeHtml(e.category || '')}</span> ${escapeHtml(e.description || '')}`
        : '—';

      const statusBadge = e.refundStatus
        ? `<span class="badge ${e.refundStatus === 'RECEIVED' ? 'badge-basis-user-reported' : 'badge-neutral'}" title="${e.refundStatus === 'RECEIVED' ? 'User-reported, not verified' : ''}">${escapeHtml(e.refundStatus)}</span>`
        : '—';

      const noteParts = [];
      if (e.note) noteParts.push(escapeHtml(e.note));
      if (e.reference) noteParts.push(`<span class="tracker-meta-chip">Ref: ${escapeHtml(e.reference)}</span>`);
      if (e.receivedOn) noteParts.push(`<span style="font-size:0.75rem; color:#94a3b8;">(${escapeHtml(e.receivedOn)})</span>`);
      const notesHtml = noteParts.join(' ') || '—';

      const isRefundRow = (e.type === 'REFUND' || e.type === 'REFUND_RECORDED');
      const actionsHtml = isRefundRow
        ? `<button type="button" class="btn btn-outline btn-xs btn-share-refund" data-entry-id="${escapeHtml(e.id)}">Share</button>`
        : '—';

      return `
        <tr>
          <td><span class="tracker-action-type">${escapeHtml(e.type || '')}</span></td>
          <td>${escapeHtml(bookingLabel)}</td>
          <td class="${amountClass}">${amountStr}</td>
          <td><span class="badge ${basisClass}">${escapeHtml(basis)}</span></td>
          <td>${catDesc}</td>
          <td>${statusBadge}</td>
          <td>${notesHtml}</td>
          <td>${actionsHtml}</td>
        </tr>
      `;
    }).join('');
  }

  function addDeductionRow(category = 'CANCELLATION_CHARGE', amount = '', description = '') {
    if (!elements.refundDeductionsList) return;
    const row = document.createElement('div');
    row.className = 'refund-deduction-item';
    row.style.cssText = 'display: flex; gap: 0.5rem; align-items: center;';
    row.innerHTML = `
      <select class="form-control form-control-sm deduction-category" style="flex: 1.5;">
        <option value="CANCELLATION_CHARGE" ${category === 'CANCELLATION_CHARGE' ? 'selected' : ''}>Cancellation Charge</option>
        <option value="CONVENIENCE_FEE" ${category === 'CONVENIENCE_FEE' ? 'selected' : ''}>Convenience Fee</option>
        <option value="PARTIAL_REFUND" ${category === 'PARTIAL_REFUND' ? 'selected' : ''}>Partial Refund</option>
        <option value="PENALTY" ${category === 'PENALTY' ? 'selected' : ''}>Penalty</option>
        <option value="OTHER" ${category === 'OTHER' ? 'selected' : ''}>Other</option>
      </select>
      <input type="number" class="form-control form-control-sm deduction-amount" step="0.01" min="0.01" placeholder="Amount (INR)" value="${escapeHtml(amount)}" style="flex: 1;" required>
      <input type="text" class="form-control form-control-sm deduction-description" minlength="5" maxlength="200" placeholder="Reason (5-200 chars)" value="${escapeHtml(description)}" style="flex: 2.5;" required>
      <button type="button" class="btn btn-outline btn-xs btn-remove-deduction" style="color: #ef4444; border-color: #ef4444;" aria-label="Remove deduction">&times;</button>
    `;

    row.querySelector('.btn-remove-deduction').addEventListener('click', () => {
      row.remove();
      updateLiveRefundPreview();
    });

    row.querySelector('.deduction-amount').addEventListener('input', updateLiveRefundPreview);
    row.querySelector('.deduction-description').addEventListener('input', updateLiveRefundPreview);
    elements.refundDeductionsList.appendChild(row);
    updateLiveRefundPreview();
  }

  function getSelectedBookingCost() {
    if (!elements.ledgerRefundBookingSelect || !state.trip || !state.trip.bookings) return null;
    const bId = elements.ledgerRefundBookingSelect.value;
    if (!bId) return null;
    const b = state.trip.bookings.find(bk => String(bk.id) === String(bId));
    return b && b.cost !== undefined ? Number(b.cost) : null;
  }

  function updateLiveRefundPreview() {
    const gross = elements.ledgerRefundAmountInput ? parseFloat(elements.ledgerRefundAmountInput.value) || 0 : 0;
    let totalDeductions = 0;
    if (elements.refundDeductionsList) {
      const rows = elements.refundDeductionsList.querySelectorAll('.refund-deduction-item');
      rows.forEach(r => {
        const amt = parseFloat(r.querySelector('.deduction-amount').value) || 0;
        totalDeductions += amt;
      });
    }

    const net = Math.max(0, gross - totalDeductions);
    if (elements.refundLiveNetPreview) {
      elements.refundLiveNetPreview.textContent = `Net Refund: Rs ${net.toFixed(2)} (Gross: Rs ${gross.toFixed(2)} - Deductions: Rs ${totalDeductions.toFixed(2)})`;
    }

    const bookingCost = getSelectedBookingCost();
    if (elements.refundLiveReductionPreview) {
      if (bookingCost !== null && net < bookingCost) {
        const reduction = bookingCost - net;
        elements.refundLiveReductionPreview.textContent = `Reduction: Rs ${reduction.toFixed(2)} below booking cost (explanation required)`;
        elements.refundLiveReductionPreview.classList.remove('hidden');
      } else {
        elements.refundLiveReductionPreview.classList.add('hidden');
      }
    }
  }

  async function handleRecordRefund() {
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    if (!tripId) {
      showToast('No trip selected', 'warning');
      return;
    }

    if (elements.refundFormError) {
      elements.refundFormError.classList.add('hidden');
      elements.refundFormError.textContent = '';
    }

    const bookingId = elements.ledgerRefundBookingSelect ? elements.ledgerRefundBookingSelect.value : '';
    const amountVal = elements.ledgerRefundAmountInput ? parseFloat(elements.ledgerRefundAmountInput.value) : NaN;
    const statusVal = elements.ledgerRefundStatusSelect ? elements.ledgerRefundStatusSelect.value : 'RECEIVED';
    const receivedOn = elements.ledgerRefundDateInput ? elements.ledgerRefundDateInput.value : '';
    const reference = elements.ledgerRefundRefInput ? elements.ledgerRefundRefInput.value.trim() : '';
    const note = elements.ledgerRefundNoteInput ? elements.ledgerRefundNoteInput.value.trim() : '';

    if (!bookingId) {
      showRefundFormError('Please select a booking to record a refund for.');
      return;
    }
    if (isNaN(amountVal) || amountVal <= 0) {
      showRefundFormError('Gross refund amount must be greater than zero.');
      return;
    }

    const bookingCost = getSelectedBookingCost();
    if (bookingCost !== null && amountVal > bookingCost) {
      showRefundFormError(`Gross refund cannot exceed booking cost of Rs ${bookingCost.toFixed(2)}.`);
      return;
    }

    // Collect deductions
    const deductions = [];
    if (elements.refundDeductionsList) {
      const rows = elements.refundDeductionsList.querySelectorAll('.refund-deduction-item');
      for (const r of rows) {
        const cat = r.querySelector('.deduction-category').value;
        const dAmt = parseFloat(r.querySelector('.deduction-amount').value);
        const desc = r.querySelector('.deduction-description').value.trim();
        if (isNaN(dAmt) || dAmt <= 0) {
          showRefundFormError('Each deduction amount must be greater than zero.');
          return;
        }
        if (!desc || desc.length < 5 || desc.length > 200) {
          showRefundFormError('Each deduction description must be between 5 and 200 characters.');
          return;
        }
        deductions.push({ category: cat, amount: dAmt, description: desc });
      }
    }

    const totalDeductions = deductions.reduce((s, d) => s + d.amount, 0);
    if (totalDeductions > amountVal) {
      showRefundFormError(`Total deductions (Rs ${totalDeductions.toFixed(2)}) cannot exceed gross refund (Rs ${amountVal.toFixed(2)}).`);
      return;
    }

    const netRefund = amountVal - totalDeductions;
    if (bookingCost !== null && netRefund < bookingCost && deductions.length === 0) {
      showRefundFormError(`A reduced refund must be explained: Net refund is Rs ${netRefund.toFixed(2)}, which is less than booking cost Rs ${bookingCost.toFixed(2)}. At least one deduction with a description is required.`);
      return;
    }

    setButtonLoading(elements.btnSubmitRecordRefund, true);

    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/ledger/refunds`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          bookingId,
          grossAmount: amountVal,
          status: statusVal,
          deductions,
          reference: reference || null,
          receivedOn: receivedOn || null,
          note: note || null,
        }),
      });

      const payload = await res.json();
      if (!res.ok || !payload.success) {
        throw new Error(payload.message || 'Failed to record refund');
      }

      renderLedger(payload.data);
      showToast('Refund recorded successfully (User-reported, not verified).', 'success');

      // Clear inputs
      if (elements.ledgerRefundAmountInput) elements.ledgerRefundAmountInput.value = '';
      if (elements.ledgerRefundRefInput) elements.ledgerRefundRefInput.value = '';
      if (elements.ledgerRefundNoteInput) elements.ledgerRefundNoteInput.value = '';
      if (elements.refundDeductionsList) elements.refundDeductionsList.innerHTML = '';
      updateLiveRefundPreview();

      await refreshTripAuditOnly(tripId);
    } catch (err) {
      showRefundFormError(err.message);
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnSubmitRecordRefund, false);
    }
  }

  function showRefundFormError(msg) {
    if (elements.refundFormError) {
      elements.refundFormError.textContent = msg;
      elements.refundFormError.classList.remove('hidden');
    }
    showToast(msg, 'warning');
  }

  function handleExportLedgerCsv() {
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    if (!tripId) {
      showToast('No trip selected', 'warning');
      return;
    }
    window.location.href = `/api/trips/${encodeURIComponent(tripId)}/ledger/export`;
  }

  // ==========================================
  // Phase 12 Add-on: Share Refund Summary Modal
  // ==========================================

  function openShareRefundModal(entryId) {
    if (!elements.modalShareRefund) return;
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    if (!tripId || !entryId) return;

    if (elements.shareRefundEntryId) elements.shareRefundEntryId.value = entryId;
    if (elements.shareRefundChannelSelect) elements.shareRefundChannelSelect.value = 'EMAIL';
    if (elements.shareRefundConfirmCheck) elements.shareRefundConfirmCheck.checked = true;
    if (elements.shareRefundStatusBanner) elements.shareRefundStatusBanner.classList.add('hidden');

    updateShareRecipientAndPreview();
    elements.modalShareRefund.classList.remove('hidden');
  }

  function closeShareRefundModal() {
    if (elements.modalShareRefund) {
      elements.modalShareRefund.classList.add('hidden');
    }
  }

  function updateShareRecipientAndPreview() {
    const channel = elements.shareRefundChannelSelect ? elements.shareRefundChannelSelect.value : 'EMAIL';
    const trip = state.trip;
    const recipientInput = elements.shareRefundRecipientInput;

    if (recipientInput && trip) {
      if (channel === 'EMAIL' || channel === 'MAILTO') {
        recipientInput.placeholder = 'user@example.com';
        recipientInput.value = trip.travellerEmail || '';
      } else if (channel === 'SMS') {
        recipientInput.placeholder = '+91XXXXXXXXXX';
        recipientInput.value = trip.travellerPhone || '';
      } else {
        recipientInput.placeholder = 'Not applicable for plain text copy';
        recipientInput.value = '';
      }
    }

    if (elements.shareRefundPreview && trip) {
      const isSms = channel === 'SMS';
      const traveller = trip.travellerName || 'Traveller';
      const tripTitle = trip.title || 'Expedition';

      let previewText = '';
      if (isSms) {
        previewText = `Odyssey Refund: ${tripTitle} - ${traveller}. Gross and Net calculated with deductions. Refunds take 5-7 days. User-reported, not verified.`;
      } else {
        previewText = `Odyssey Trip: ${tripTitle}\nTraveller: ${traveller}\n\nRefund Summary (User-reported, not verified)\nAmounts are user-reported and not verified by the operator.\n\nRefunds are not instant. They usually take a few working days (typically 5 to 7).`;
      }
      elements.shareRefundPreview.textContent = previewText;
    }
  }

  async function handleConfirmShareRefund() {
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    const entryId = elements.shareRefundEntryId ? elements.shareRefundEntryId.value : '';
    const channel = elements.shareRefundChannelSelect ? elements.shareRefundChannelSelect.value : 'EMAIL';
    const recipient = elements.shareRefundRecipientInput ? elements.shareRefundRecipientInput.value.trim() : '';
    const confirmed = elements.shareRefundConfirmCheck ? elements.shareRefundConfirmCheck.checked : false;

    if (!tripId || !entryId) {
      showToast('No refund entry selected', 'warning');
      return;
    }
    if (!confirmed) {
      showToast('Please confirm sharing by checking the box.', 'warning');
      return;
    }

    setButtonLoading(elements.btnConfirmShareRefund, true);

    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/ledger/refunds/${encodeURIComponent(entryId)}/share`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          channel,
          recipient: recipient || null,
          confirm: true,
        }),
      });

      const payload = await res.json();
      if (!res.ok || !payload.success) {
        throw new Error(payload.message || 'Failed to share refund summary');
      }

      const data = payload.data || {};
      if (channel === 'MAILTO' && data.mailtoUrl) {
        window.location.href = data.mailtoUrl;
      } else if (channel === 'COPY' && data.content) {
        if (navigator.clipboard) {
          await navigator.clipboard.writeText(data.content);
        }
      }

      const statusText = (data.status === 'LINK_CREATED' || data.status === 'COPIED')
        ? 'Not sent by Odyssey'
        : (data.status || 'SENT');
      showToast(`Refund summary shared via ${channel} (Status: ${statusText})`, 'success');
      closeShareRefundModal();
      await loadNotificationsHistory();
      await refreshTripAuditOnly(tripId);
    } catch (err) {
      showToast(err.message, 'danger');
      if (elements.shareRefundStatusBanner) {
        elements.shareRefundStatusBanner.textContent = err.message;
        elements.shareRefundStatusBanner.className = 'banner banner-danger';
        elements.shareRefundStatusBanner.classList.remove('hidden');
      }
    } finally {
      setButtonLoading(elements.btnConfirmShareRefund, false);
    }
  }

  // ==========================================
  // Phase 13: Traveller Communication Assistant
  // ==========================================

  let currentMessagesList = [];

  async function loadTripMessages(tripId) {
    if (!elements.sectionMessages || !tripId) return;
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/messages`);
      if (!res.ok) {
        const errPayload = await res.json().catch(() => ({}));
        const msg = errPayload.message || (res.status === 403 ? 'Access denied: Messages are visible only to the trip owner.' : `Failed to fetch messages (HTTP ${res.status})`);
        if (elements.messagesTableBody) {
          elements.messagesTableBody.innerHTML = `<tr><td colspan="8" class="empty-state" style="color: #f87171;">${escapeHtml(msg)}</td></tr>`;
        }
        return;
      }
      const data = await res.json();
      currentMessagesList = (data.success && Array.isArray(data.data)) ? data.data : [];
      renderMessagesHistory(currentMessagesList);
    } catch (err) {
      console.warn('Error loading trip messages:', err);
      if (elements.messagesTableBody) {
        elements.messagesTableBody.innerHTML = `<tr><td colspan="8" class="empty-state" style="color: #f87171;">Unable to load messages: ${escapeHtml(err.message)}</td></tr>`;
      }
    }
  }

  function renderMessagesHistory(messages) {
    if (!elements.messagesTableBody) return;
    if (elements.messagesCountBadge) {
      const count = messages.length;
      elements.messagesCountBadge.textContent = count === 1 ? '1 message' : `${count} messages`;
    }

    if (!messages || messages.length === 0) {
      elements.messagesTableBody.innerHTML = `
        <tr>
          <td colspan="8" class="empty-state">No communications drafted yet for this trip.</td>
        </tr>
      `;
      return;
    }

    elements.messagesTableBody.innerHTML = messages.map(msg => {
      const timeStr = msg.createdAt ? String(msg.createdAt).replace('T', ' ').slice(0, 16) : '-';
      const statusBadge = getMessageStatusBadge(msg.status, msg.statusLabel);
      const genByBadge = msg.generatedBy === 'LLM'
        ? '<span class="badge badge-purple" title="Verified against source data">Verified LLM</span>'
        : '<span class="badge badge-info" title="Deterministic stored data template">Template</span>';

      return `
        <tr data-message-id="${escapeHtml(msg.id)}">
          <td><span class="text-sm font-mono">${escapeHtml(timeStr)}</span></td>
          <td><span class="badge badge-neutral">${escapeHtml(msg.recipientType || '')}</span></td>
          <td><span class="text-sm">${escapeHtml(msg.draftKind || '')}</span></td>
          <td><span class="text-sm font-mono">${escapeHtml(msg.channel || 'None')}</span></td>
          <td><span class="text-sm font-mono">${escapeHtml(msg.recipientMasked || '-')}</span></td>
          <td>${genByBadge}</td>
          <td>${statusBadge}</td>
          <td>
            <button type="button" class="btn btn-outline btn-xs btn-view-message" data-msg-id="${escapeHtml(msg.id)}">
              View
            </button>
          </td>
        </tr>
      `;
    }).join('');
  }

  function getMessageStatusBadge(status, label) {
    const text = label || status || 'DRAFT';
    switch (status) {
      case 'SENT':
        return `<span class="badge badge-success">${escapeHtml(text)}</span>`;
      case 'SIMULATED':
        return `<span class="badge badge-warning" title="Simulated, not sent">${escapeHtml(text)}</span>`;
      case 'LINK_CREATED':
      case 'COPIED':
        return `<span class="badge badge-info" title="Not sent by Odyssey">${escapeHtml(text)}</span>`;
      case 'SKIPPED':
        return `<span class="badge badge-neutral">${escapeHtml(text)}</span>`;
      case 'FAILED':
        return `<span class="badge badge-danger">${escapeHtml(text)}</span>`;
      case 'APPROVED':
        return `<span class="badge badge-purple">${escapeHtml(text)}</span>`;
      case 'EDITED':
        return `<span class="badge badge-gold">${escapeHtml(text)}</span>`;
      case 'DRAFT':
      default:
        return `<span class="badge badge-neutral">${escapeHtml(text)}</span>`;
    }
  }

  function populateMessageBookingDropdown(bookings) {
    if (!elements.selectMsgBooking) return;
    const currentVal = elements.selectMsgBooking.value;
    elements.selectMsgBooking.innerHTML = '<option value="">-- Trip Level / No Specific Booking --</option>';

    if (bookings && bookings.length > 0) {
      bookings.forEach(b => {
        const opt = document.createElement('option');
        opt.value = b.id;
        const prov = b.provider || 'Provider';
        const route = (b.from && b.to) ? ` (${b.from} -> ${b.to})` : '';
        const ref = b.bookingReference ? ` [${b.bookingReference}]` : '';
        opt.textContent = `${b.id} - ${prov}${route}${ref}`;
        elements.selectMsgBooking.appendChild(opt);
      });
    }
    if (currentVal) {
      elements.selectMsgBooking.value = currentVal;
    }
  }

  function updateMsgCharCounter() {
    if (!elements.textareaMsgBody || !elements.msgCharCounter) return;
    const len = elements.textareaMsgBody.value.length;
    elements.msgCharCounter.textContent = `${len} / 4000`;
    if (len > 4000) {
      elements.msgCharCounter.style.color = 'var(--status-red)';
    } else {
      elements.msgCharCounter.style.color = 'var(--text-dim)';
    }
  }

  function displayActiveMessage(msg) {
    if (!elements.msgActiveEditorContainer || !msg) return;
    elements.msgActiveEditorContainer.classList.remove('hidden');
    if (elements.inputActiveMsgId) elements.inputActiveMsgId.value = msg.id;
    if (elements.inputMsgSubject) elements.inputMsgSubject.value = msg.subject || '';
    if (elements.textareaMsgBody) elements.textareaMsgBody.value = msg.body || '';
    updateMsgCharCounter();

    if (elements.badgeMsgGeneratedBy) {
      elements.badgeMsgGeneratedBy.textContent = msg.generatedByLabel || msg.generatedBy;
      elements.badgeMsgGeneratedBy.className = `badge ${msg.generatedBy === 'LLM' ? 'badge-purple' : 'badge-info'}`;
    }
    if (elements.badgeMsgStatus) {
      elements.badgeMsgStatus.textContent = msg.statusLabel || msg.status;
      elements.badgeMsgStatus.className = `badge ${msg.status === 'SENT' ? 'badge-success' : (msg.status === 'SIMULATED' ? 'badge-warning' : 'badge-neutral')}`;
    }

    if (elements.inputMsgRecipient) {
      if (msg.recipientMasked) {
        elements.inputMsgRecipient.placeholder = `Masked contact on file: ${msg.recipientMasked}`;
      } else {
        elements.inputMsgRecipient.placeholder = "recipient@example.com or 9876543210";
      }
    }

    if (elements.msgSendConfirmBox) elements.msgSendConfirmBox.classList.add('hidden');
    if (elements.msgDispatchResultBanner) elements.msgDispatchResultBanner.classList.add('hidden');
  }

  async function handleGenerateDraft() {
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    if (!tripId) {
      showToast('Please select or load a trip first', 'warning');
      return;
    }

    const recipientType = elements.selectMsgRecipientType ? elements.selectMsgRecipientType.value : 'HOTEL';
    const draftKind = elements.selectMsgDraftKind ? elements.selectMsgDraftKind.value : 'LATE_CHECKIN';
    const bookingId = elements.selectMsgBooking ? elements.selectMsgBooking.value : null;

    setButtonLoading(elements.btnGenerateDraft, true);
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/messages/draft`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recipientType: recipientType,
          draftKind: draftKind,
          bookingId: bookingId && bookingId.trim() ? bookingId.trim() : null
        })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to generate message draft');
      }

      const msg = data.data;
      displayActiveMessage(msg);
      showToast(`Draft generated (${msg.generatedByLabel || msg.generatedBy})`, 'success');
      await loadTripMessages(tripId);
    } catch (err) {
      console.error('Draft generation error:', err);
      showToast(err.message || 'Error generating message draft', 'danger');
    } finally {
      setButtonLoading(elements.btnGenerateDraft, false);
    }
  }

  async function handleSaveMsgEdits() {
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    const msgId = elements.inputActiveMsgId ? elements.inputActiveMsgId.value : null;
    if (!tripId || !msgId) {
      showToast('No active message to save', 'warning');
      return;
    }

    const subject = elements.inputMsgSubject ? elements.inputMsgSubject.value.trim() : '';
    const body = elements.textareaMsgBody ? elements.textareaMsgBody.value : '';

    if (!subject) {
      showToast('Subject cannot be empty', 'warning');
      return;
    }
    if (body.length > 4000) {
      showToast('Body cannot exceed 4000 characters', 'danger');
      return;
    }

    setButtonLoading(elements.btnSaveMsgEdits, true);
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/messages/${encodeURIComponent(msgId)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subject, body })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to save message edits');
      }
      displayActiveMessage(data.data);
      showToast('Message edits saved (Status: EDITED)', 'success');
      await loadTripMessages(tripId);
    } catch (err) {
      console.error('Save message error:', err);
      showToast(err.message || 'Error saving message edits', 'danger');
    } finally {
      setButtonLoading(elements.btnSaveMsgEdits, false);
    }
  }

  async function handleSendMessage(confirm) {
    const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
    const msgId = elements.inputActiveMsgId ? elements.inputActiveMsgId.value : null;
    if (!tripId || !msgId) {
      showToast('No active message selected to send', 'warning');
      return;
    }

    const channel = elements.selectMsgChannel ? elements.selectMsgChannel.value : 'EMAIL';
    const recipient = elements.inputMsgRecipient ? elements.inputMsgRecipient.value.trim() : '';

    setButtonLoading(elements.btnConfirmSendMessage, true);
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/messages/${encodeURIComponent(msgId)}/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          channel: channel,
          recipient: recipient || null,
          confirm: confirm === true
        })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to dispatch message');
      }

      const msg = data.data;
      displayActiveMessage(msg);
      if (elements.msgSendConfirmBox) elements.msgSendConfirmBox.classList.add('hidden');

      // Banner feedback based on status
      if (elements.msgDispatchResultBanner && elements.msgDispatchResultText) {
        elements.msgDispatchResultBanner.classList.remove('hidden', 'banner-success', 'banner-warning', 'banner-info');
        let bannerText = '';
        if (msg.status === 'SENT') {
          elements.msgDispatchResultBanner.classList.add('banner-success');
          bannerText = `Message dispatched successfully via ${msg.channel}.`;
        } else if (msg.status === 'SIMULATED') {
          elements.msgDispatchResultBanner.classList.add('banner-warning');
          bannerText = `Message simulated (Simulated, not sent). Active credentials not configured for ${msg.channel}.`;
        } else if (msg.status === 'LINK_CREATED') {
          elements.msgDispatchResultBanner.classList.add('banner-info');
          bannerText = 'Mailto link generated (Not sent by Odyssey). Opening your default email client.';
          if (msg.mailtoUrl) {
            window.open(msg.mailtoUrl, '_blank');
          }
        } else if (msg.status === 'COPIED') {
          elements.msgDispatchResultBanner.classList.add('banner-info');
          bannerText = 'Message copied to clipboard (Not sent by Odyssey).';
          if (navigator.clipboard && msg.body) {
            navigator.clipboard.writeText(msg.body).catch(() => { });
          }
        }
        elements.msgDispatchResultText.textContent = bannerText;
      }

      showToast(`Communication dispatched: ${msg.statusLabel || msg.status}`, 'success');
      await loadTripMessages(tripId);
      loadNotificationHistory(tripId);
      refreshTripAuditOnly(tripId);
    } catch (err) {
      console.error('Send message error:', err);
      showToast(err.message || 'Error dispatching message', 'danger');
      if (elements.msgDispatchResultBanner && elements.msgDispatchResultText) {
        elements.msgDispatchResultBanner.classList.remove('hidden', 'banner-success', 'banner-info');
        elements.msgDispatchResultBanner.classList.add('banner-warning');
        elements.msgDispatchResultText.textContent = err.message || 'Failed to send message';
      }
    } finally {
      setButtonLoading(elements.btnConfirmSendMessage, false);
    }
  }

  function prepareAndDraftHotelMessage(bookingId) {
    if (elements.selectMsgRecipientType) elements.selectMsgRecipientType.value = 'HOTEL';
    if (elements.selectMsgDraftKind) elements.selectMsgDraftKind.value = 'LATE_CHECKIN';
    if (elements.selectMsgBooking && bookingId) {
      elements.selectMsgBooking.value = bookingId;
    }
    if (elements.sectionMessages) {
      elements.sectionMessages.scrollIntoView({ behavior: 'smooth' });
    }
    handleGenerateDraft();
  }

  // ==========================================
  // Phase 14: Trip Wallet
  // ==========================================

  async function loadTripWallet(tripId) {
    if (!elements.sectionWallet || !tripId) return;
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/wallet`);
      if (res.status === 401) {
        showToast('Please sign in to view the Trip Wallet', 'warning');
        return;
      }
      if (res.status === 403) {
        if (elements.walletBookingsContainer) {
          elements.walletBookingsContainer.innerHTML = '<p class="empty-state alert-stale-notice">Access denied: You do not have permission to view this trip\'s wallet.</p>';
        }
        return;
      }
      if (res.status === 404) {
        if (elements.walletBookingsContainer) {
          elements.walletBookingsContainer.innerHTML = '<p class="empty-state">Trip not found.</p>';
        }
        return;
      }
      if (!res.ok) {
        throw new Error(`Failed to load wallet (HTTP ${res.status})`);
      }
      const data = await res.json();
      if (!data.success || !data.data) {
        throw new Error(data.message || 'No wallet data returned');
      }

      state.walletData = data.data;
      renderTripWallet(data.data);
    } catch (err) {
      console.warn('Failed to fetch wallet for trip', tripId, err);
      if (elements.walletBookingsContainer) {
        elements.walletBookingsContainer.innerHTML = `<p class="empty-state">Unable to load trip wallet: ${escapeHtml(err.message)}</p>`;
      }
    }
  }

  function renderTripWallet(wallet) {
    if (!wallet) return;

    // Render Ledger Summary Line if available
    if (elements.walletLedgerSummaryLine) {
      if (wallet.ledgerSummary && wallet.ledgerSummary.netCost != null) {
        elements.walletLedgerSummaryLine.classList.remove('hidden');
        let refundPart = '';
        if (wallet.ledgerSummary.outstandingRefunds != null) {
          refundPart = ` &bull; Outstanding Refunds: <strong>₹${wallet.ledgerSummary.outstandingRefunds}</strong>`;
        }
        if (wallet.ledgerSummary.refundStatus) {
          refundPart += ` (${escapeHtml(wallet.ledgerSummary.refundStatus)})`;
        }
        let timingPart = '';
        if (wallet.ledgerSummary.timingNote) {
          timingPart = `<br><span style="color: var(--text-muted); font-size: 0.8rem; font-style: italic;">${escapeHtml(wallet.ledgerSummary.timingNote)}</span>`;
        }
        elements.walletLedgerSummaryLine.innerHTML = `
          <strong>Cost &amp; Refund Ledger:</strong> Net Cost: <strong>₹${wallet.ledgerSummary.netCost}</strong>${refundPart}${timingPart}
        `;
      } else {
        elements.walletLedgerSummaryLine.classList.add('hidden');
        elements.walletLedgerSummaryLine.innerHTML = '';
      }
    }

    // Render Bookings Timeline
    if (!elements.walletBookingsContainer) return;
    const bookings = wallet.bookings || [];
    if (bookings.length === 0) {
      elements.walletBookingsContainer.innerHTML = '<p class="empty-state">No bookings in this trip wallet.</p>';
      return;
    }

    let html = '';
    bookings.forEach((b) => {
      const schedLabel = b.scheduleLabel || 'REAL';
      let chipClass = 'chip-schedule-real';
      if (schedLabel === 'GENERATED') chipClass = 'chip-schedule-gen';
      else if (schedLabel === 'ESTIMATED') chipClass = 'chip-schedule-est';

      const type = escapeHtml(b.type || 'BOOKING');
      const provider = escapeHtml(b.provider || 'Provider');
      const statusBadge = getStatusBadgeClass(b.status);
      const statusText = formatStatus(b.status || 'CONFIRMED');
      const provSuspendedBadge = (b.providerStatus === 'SUSPENDED')
        ? '<span class="badge badge-danger" style="margin-left: 0.5rem;">Provider suspended</span>'
        : (b.providerStatus ? `<span class="badge badge-danger" style="margin-left: 0.5rem;">${escapeHtml(b.providerStatus)}</span>` : '');
      const cannotHonourBadge = b.cannotHonour ? '<span class="badge badge-warning" style="margin-left: 0.5rem;">⚠️ Cannot honour</span>' : '';

      // Stored booking details
      let detailsHtml = `
        <div class="wallet-section-box">
          <div class="wallet-section-title">Stored booking details</div>
          <div class="wallet-field-row"><span class="wallet-field-label">Status</span><span class="wallet-field-value"><span class="badge ${statusBadge}">${escapeHtml(statusText)}</span>${provSuspendedBadge}${cannotHonourBadge}</span></div>
      `;
      if (b.cannotHonour) {
        detailsHtml += `<div class="wallet-field-row"><span class="wallet-field-label" style="color: #fef08a;">Honour alert</span><span class="wallet-field-value" style="color: #fef08a;">Hotel reported it cannot honour: "${escapeHtml(b.cannotHonourReason || '')}"</span></div>`;
      }
      if (b.providerStatus === 'SUSPENDED') {
        detailsHtml += `<div class="wallet-field-row"><span class="wallet-field-label" style="color: #fca5a5;">Provider notice</span><span class="wallet-field-value" style="color: #fca5a5;">Provider suspended by Odyssey administration</span></div>`;
      }
      if (b.cabServiceContact) {
        detailsHtml += `<div class="wallet-field-row"><span class="wallet-field-label">Cab contact</span><span class="wallet-field-value" style="color: #60a5fa; font-weight: 600;">${escapeHtml(b.cabServiceContact)}</span></div>`;
      } else if (b.providerContactEmail || b.providerContactPhone) {
        const contactStr = [b.providerContactEmail, b.providerContactPhone].filter(Boolean).join(' / ');
        detailsHtml += `<div class="wallet-field-row"><span class="wallet-field-label">Provider contact</span><span class="wallet-field-value">${escapeHtml(contactStr)}</span></div>`;
      }
      if (b.assignedDriverName) {
        detailsHtml += `<div class="wallet-field-row"><span class="wallet-field-label">Assigned driver</span><span class="wallet-field-value">${escapeHtml(b.assignedDriverName)}</span></div>`;
      }
      if (b.bookingReference) {
        detailsHtml += `<div class="wallet-field-row"><span class="wallet-field-label">Reference</span><span class="wallet-field-value mono-tag">${escapeHtml(b.bookingReference)}</span></div>`;
      }
      if (b.vehicleNumber) {
        detailsHtml += `<div class="wallet-field-row"><span class="wallet-field-label">Vehicle</span><span class="wallet-field-value">${escapeHtml(b.vehicleNumber)}</span></div>`;
      }
      if (b.seats) {
        detailsHtml += `<div class="wallet-field-row"><span class="wallet-field-label">Seats</span><span class="wallet-field-value">${escapeHtml(b.seats)}</span></div>`;
      }
      if (b.passengerNames) {
        detailsHtml += `<div class="wallet-field-row"><span class="wallet-field-label">Passengers</span><span class="wallet-field-value">${escapeHtml(b.passengerNames)}</span></div>`;
      }
      detailsHtml += `
        <div class="wallet-field-row"><span class="wallet-field-label">Route</span><span class="wallet-field-value">${escapeHtml(b.from || '')} &rarr; ${escapeHtml(b.to || '')}</span></div>
        <div class="wallet-field-row"><span class="wallet-field-label">Departure</span><span class="wallet-field-value">${b.startTime ? formatDateTime(b.startTime) : '-'}</span></div>
        <div class="wallet-field-row"><span class="wallet-field-label">Arrival</span><span class="wallet-field-value">${b.endTime ? formatDateTime(b.endTime) : '-'}</span></div>
        <div class="wallet-field-row"><span class="wallet-field-label">Cost</span><span class="wallet-field-value">₹${b.cost != null ? b.cost : 0}</span></div>
      </div>`;

      // Last known status block (may be out of date)
      const lks = b.lastKnownStatus || {};
      const lksState = escapeHtml(lks.state || 'UNKNOWN');
      const lksDelay = lks.delayMinutes != null ? `${lks.delayMinutes} mins` : '0 mins';
      const lksChecked = lks.checkedAt ? formatDateTime(lks.checkedAt) : 'Not checked';
      const lksSource = escapeHtml(lks.source || 'UNKNOWN');

      const statusBlockHtml = `
        <div class="wallet-section-box">
          <div class="wallet-section-title">Last known status (may be out of date)</div>
          <div class="wallet-field-row"><span class="wallet-field-label">State</span><span class="wallet-field-value"><span class="badge ${getStatusBadgeClass(lks.state)}">${lksState}</span></span></div>
          <div class="wallet-field-row"><span class="wallet-field-label">Delay</span><span class="wallet-field-value">${lksDelay}</span></div>
          <div class="wallet-field-row"><span class="wallet-field-label">Checked at</span><span class="wallet-field-value">${lksChecked}</span></div>
          <div class="wallet-field-row"><span class="wallet-field-label">Source</span><span class="wallet-field-value mono-tag">${lksSource}</span></div>
        </div>
      `;

      html += `
        <div class="wallet-booking-card">
          <div class="wallet-card-header">
            <div class="wallet-card-title">
              <span>${type}</span> &bull; <span>${provider}</span>
            </div>
            <div>
              <span class="${chipClass}">${escapeHtml(schedLabel)}</span>
            </div>
          </div>
          <div class="wallet-card-grid">
            ${detailsHtml}
            ${statusBlockHtml}
          </div>
        </div>
      `;
    });

    elements.walletBookingsContainer.innerHTML = html;
  }

  // ==========================================
  // Phase 15: Trip Readiness Score
  // ==========================================

  async function loadTripReadiness(tripId) {
    if (!elements.sectionReadiness || !tripId) return;
    try {
      const res = await fetch(`/api/trips/${encodeURIComponent(tripId)}/readiness`);
      if (res.status === 401) {
        showToast('Please sign in to view Trip Readiness', 'warning');
        renderReadinessError('Please sign in to view readiness analysis.');
        return;
      }
      if (res.status === 403) {
        renderReadinessError("Access denied: You do not have permission to view this trip's readiness.");
        return;
      }
      if (res.status === 404) {
        renderReadinessError('Trip not found.');
        return;
      }
      if (!res.ok) {
        throw new Error(`Failed to load readiness (HTTP ${res.status})`);
      }
      const data = await res.json();
      if (!data.success || !data.data) {
        throw new Error(data.message || 'No readiness data returned');
      }
      state.readinessData = data.data;
      renderTripReadiness(data.data);
    } catch (err) {
      console.warn('Failed to fetch readiness for trip', tripId, err);
      renderReadinessError(`Unable to load readiness: ${escapeHtml(err.message)}`);
    }
  }

  function renderReadinessError(message) {
    if (elements.readinessScoreDisplay) elements.readinessScoreDisplay.textContent = '--';
    if (elements.readinessBandBadge) {
      elements.readinessBandBadge.className = 'badge badge-neutral';
      elements.readinessBandBadge.textContent = '[?] UNKNOWN';
    }
    if (elements.readinessReliabilityMsg) elements.readinessReliabilityMsg.textContent = message;
    if (elements.readinessFactorsTbody) {
      elements.readinessFactorsTbody.innerHTML = `<tr><td colspan="4" class="empty-state">${escapeHtml(message)}</td></tr>`;
    }
    if (elements.readinessUnknownContainer) elements.readinessUnknownContainer.classList.add('hidden');
    if (elements.readinessRecommendationsList) {
      elements.readinessRecommendationsList.innerHTML = `<li class="empty-state" style="list-style: none;">${escapeHtml(message)}</li>`;
    }
  }

  function renderTripReadiness(readiness) {
    if (!readiness) return;

    // 1. Score & Band display
    if (elements.readinessScoreDisplay) {
      elements.readinessScoreDisplay.textContent = readiness.score != null ? readiness.score : '--';
    }

    if (elements.readinessBandBadge) {
      const band = readiness.band;
      if (!readiness.reliable || !band) {
        elements.readinessBandBadge.className = 'badge badge-neutral';
        elements.readinessBandBadge.textContent = '[?] NOT ENOUGH DATA';
      } else if (band === 'HIGH') {
        elements.readinessBandBadge.className = 'badge badge-safe';
        elements.readinessBandBadge.textContent = '● HIGH READINESS';
      } else if (band === 'MEDIUM') {
        elements.readinessBandBadge.className = 'badge badge-amber';
        elements.readinessBandBadge.textContent = '▲ MEDIUM READINESS';
      } else {
        elements.readinessBandBadge.className = 'badge badge-red';
        elements.readinessBandBadge.textContent = '▼ LOW READINESS';
      }
    }

    if (elements.readinessReliabilityMsg) {
      if (!readiness.reliable) {
        elements.readinessReliabilityMsg.textContent = readiness.statusMessage || 'Not enough data for a reliable score';
        elements.readinessReliabilityMsg.style.color = '#f59e0b';
      } else {
        const knownCount = readiness.factors ? readiness.factors.filter(f => f.status === 'KNOWN').length : 0;
        elements.readinessReliabilityMsg.textContent = `Verified across ${knownCount} active factor(s)`;
        elements.readinessReliabilityMsg.style.color = '#94a3b8';
      }
    }

    // 2. Factor Breakdown Table
    if (elements.readinessFactorsTbody) {
      const factors = readiness.factors || [];
      if (factors.length === 0) {
        elements.readinessFactorsTbody.innerHTML = '<tr><td colspan="4" class="empty-state">No factors evaluated.</td></tr>';
      } else {
        const rows = factors.map(f => {
          const isKnown = f.status === 'KNOWN';
          const pointsDisplay = isKnown && f.pointsEarned != null
            ? `<strong>${f.pointsEarned}</strong> / ${f.pointsPossible}`
            : '<span class="badge badge-neutral" style="font-size: 0.75rem;">[EXCLUDED]</span>';

          let dataDetails = '-';
          if (f.dataUsed && typeof f.dataUsed === 'object') {
            dataDetails = Object.entries(f.dataUsed)
              .map(([k, v]) => `<span style="display: inline-block; margin-right: 8px; font-family: monospace;">${escapeHtml(k)}: ${escapeHtml(String(v))}</span>`)
              .join(' ');
          }

          return `
            <tr>
              <td><strong>${escapeHtml(f.name || f.id)}</strong></td>
              <td>${pointsDisplay}</td>
              <td style="font-size: 0.85rem; color: #cbd5e1;">${escapeHtml(f.explanation || '')}</td>
              <td style="font-size: 0.8rem; color: #94a3b8;">${dataDetails}</td>
            </tr>
          `;
        }).join('');
        elements.readinessFactorsTbody.innerHTML = rows;
      }
    }

    // 3. Unknown factors container
    if (elements.readinessUnknownContainer && elements.readinessUnknownList) {
      const unknowns = readiness.unknownFactors || [];
      if (unknowns.length > 0) {
        elements.readinessUnknownContainer.classList.remove('hidden');
        elements.readinessUnknownList.innerHTML = unknowns.map(u =>
          `<li style="margin-bottom: 4px;"><strong>${escapeHtml(u.name || u.id)}:</strong> ${escapeHtml(u.explanation || 'Excluded from denominator due to missing data.')}</li>`
        ).join('');
      } else {
        elements.readinessUnknownContainer.classList.add('hidden');
        elements.readinessUnknownList.innerHTML = '';
      }
    }

    // 4. Recommendations list
    if (elements.readinessRecommendationsList) {
      const recs = readiness.recommendations || [];
      if (recs.length === 0) {
        elements.readinessRecommendationsList.innerHTML = '<li class="empty-state" style="list-style: none;">No recommendations needed. All evaluated factors meet recommended standards.</li>';
      } else {
        elements.readinessRecommendationsList.innerHTML = recs.map(r =>
          `<li style="margin-bottom: 6px;">${escapeHtml(r.message)}</li>`
        ).join('');
      }
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
        throw new Error(errData.message || (res.status === 403 ? 'Access denied: Notifications are visible only to the trip owner.' : `Failed to load notifications history (HTTP ${res.status})`));
      }
      const data = await res.json();
      if (data.success && data.data) {
        renderNotificationHistory(data.data);
      }
    } catch (err) {
      console.warn('Failed to load notification history:', err);
      if (elements.notificationsTableBody) {
        elements.notificationsTableBody.innerHTML = `<tr><td colspan="6" class="empty-state" style="color: #f87171;">Unable to load alert history: ${escapeHtml(err.message)}</td></tr>`;
      }
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
      let displayStatus = item.status;
      if (item.status === 'SENT') {
        chipClass = 'chip-sent';
      } else if (item.status === 'FAILED') {
        chipClass = 'chip-failed';
      } else if (item.status === 'SKIPPED') {
        chipClass = 'chip-skipped';
      } else if (item.status === 'LINK_CREATED' || item.status === 'COPIED') {
        chipClass = 'chip-simulated';
        displayStatus = 'Not sent by Odyssey';
      }

      const errorNote = item.error ? `<div class="field-error" style="margin-top: 4px; font-size: 0.78rem;">${escapeHtml(item.error)}</div>` : '';

      return `
        <tr>
          <td><span class="mono">${escapeHtml(timeStr)}</span></td>
          <td><span class="badge badge-neutral">${escapeHtml(item.eventType)}</span></td>
          <td><strong>${escapeHtml(item.channel)}</strong></td>
          <td><span class="mono">${escapeHtml(item.recipientMasked || '')}</span></td>
          <td><span class="status-chip ${chipClass}">${escapeHtml(displayStatus)}</span></td>
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
    if (!tbody) return;
    const count = auditLog ? auditLog.length : 0;
    if (elements.auditCountBadge) {
      elements.auditCountBadge.textContent = count === 1 ? '1 entry' : `${count} entries`;
    }

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
    if (elements.errCreateNeedsNote) elements.errCreateNeedsNote.textContent = '';
    if (elements.errCreateEmergencyPhone) elements.errCreateEmergencyPhone.textContent = '';

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

    // Phase 17: Emergency Contact & Needs
    let normalizedEmergencyPhone = null;
    const rawEmergencyPhone = elements.createTripEmergencyPhone ? elements.createTripEmergencyPhone.value.trim() : '';
    if (rawEmergencyPhone) {
      let cleanEmerg = rawEmergencyPhone.replace(/\D/g, '');
      if (cleanEmerg.length === 12 && cleanEmerg.startsWith('91')) cleanEmerg = cleanEmerg.substring(2);
      else if (cleanEmerg.length === 11 && cleanEmerg.startsWith('0')) cleanEmerg = cleanEmerg.substring(1);
      if (cleanEmerg.length !== 10) {
        showToast('Emergency contact phone must be a valid 10-digit Indian phone number', 'warning');
        if (elements.errCreateEmergencyPhone) elements.errCreateEmergencyPhone.textContent = 'Emergency contact phone must be a valid 10-digit Indian phone number';
        return;
      }
      normalizedEmergencyPhone = '+91' + cleanEmerg;
    }

    const specialNeedsNote = elements.createTripNeedsNote ? elements.createTripNeedsNote.value.trim() : '';
    if (specialNeedsNote && specialNeedsNote.length > 300) {
      showToast('Special needs note must not exceed 300 characters', 'warning');
      if (elements.errCreateNeedsNote) elements.errCreateNeedsNote.textContent = 'Special needs note must not exceed 300 characters';
      return;
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
      hasMobilityAssistance: elements.createTripNeedMobility ? elements.createTripNeedMobility.checked : false,
      hasElderlyTraveller: elements.createTripNeedElderly ? elements.createTripNeedElderly.checked : false,
      hasTravellingWithChildren: elements.createTripNeedChildren ? elements.createTripNeedChildren.checked : false,
      hasMedicalNeeds: elements.createTripNeedMedical ? elements.createTripNeedMedical.checked : false,
      specialNeedsNote: specialNeedsNote || null,
      emergencyContactName: elements.createTripEmergencyName ? (elements.createTripEmergencyName.value.trim() || null) : null,
      emergencyContactPhone: normalizedEmergencyPhone,
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
    if (elements.btnLoadDemo) setButtonLoading(elements.btnLoadDemo, true);
    try {
      await loadTripById('TRIP-DEMO-001');
    } finally {
      if (elements.btnLoadDemo) setButtonLoading(elements.btnLoadDemo, false);
    }
  }

  async function handleResetDemo() {
    if (elements.btnReset) setButtonLoading(elements.btnReset, true);
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
      if (elements.btnReset) setButtonLoading(elements.btnReset, false);
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

    if (elements.editTripNeedMobility) elements.editTripNeedMobility.checked = !!t.hasMobilityAssistance;
    if (elements.editTripNeedElderly) elements.editTripNeedElderly.checked = !!t.hasElderlyTraveller;
    if (elements.editTripNeedChildren) elements.editTripNeedChildren.checked = !!t.hasTravellingWithChildren;
    if (elements.editTripNeedMedical) elements.editTripNeedMedical.checked = !!t.hasMedicalNeeds;
    if (elements.editTripNeedsNote) elements.editTripNeedsNote.value = t.specialNeedsNote || '';
    if (elements.editTripEmergencyName) elements.editTripEmergencyName.value = t.emergencyContactName || '';
    if (elements.editTripEmergencyPhone) {
      elements.editTripEmergencyPhone.value = t.emergencyContactPhone
        ? (t.emergencyContactPhone.startsWith('+91') ? t.emergencyContactPhone.substring(3) : t.emergencyContactPhone)
        : '';
    }
    if (elements.errEditNeedsNote) elements.errEditNeedsNote.textContent = '';
    if (elements.errEditEmergencyPhone) elements.errEditEmergencyPhone.textContent = '';

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

    // Phase 17: Emergency Contact & Needs
    let normalizedEmergencyPhone = null;
    const rawEmergencyPhone = elements.editTripEmergencyPhone ? elements.editTripEmergencyPhone.value.trim() : '';
    if (rawEmergencyPhone) {
      let cleanEmerg = rawEmergencyPhone.replace(/\D/g, '');
      if (cleanEmerg.length === 12 && cleanEmerg.startsWith('91')) cleanEmerg = cleanEmerg.substring(2);
      else if (cleanEmerg.length === 11 && cleanEmerg.startsWith('0')) cleanEmerg = cleanEmerg.substring(1);
      if (cleanEmerg.length !== 10) {
        showToast('Emergency contact phone must be a valid 10-digit Indian phone number', 'warning');
        if (elements.errEditEmergencyPhone) elements.errEditEmergencyPhone.textContent = 'Emergency contact phone must be a valid 10-digit Indian phone number';
        return;
      }
      normalizedEmergencyPhone = '+91' + cleanEmerg;
    }

    const specialNeedsNote = elements.editTripNeedsNote ? elements.editTripNeedsNote.value.trim() : '';
    if (specialNeedsNote && specialNeedsNote.length > 300) {
      showToast('Special needs note must not exceed 300 characters', 'warning');
      if (elements.errEditNeedsNote) elements.errEditNeedsNote.textContent = 'Special needs note must not exceed 300 characters';
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
      hasMobilityAssistance: elements.editTripNeedMobility ? elements.editTripNeedMobility.checked : false,
      hasElderlyTraveller: elements.editTripNeedElderly ? elements.editTripNeedElderly.checked : false,
      hasTravellingWithChildren: elements.editTripNeedChildren ? elements.editTripNeedChildren.checked : false,
      hasMedicalNeeds: elements.editTripNeedMedical ? elements.editTripNeedMedical.checked : false,
      specialNeedsNote: specialNeedsNote || null,
      emergencyContactName: elements.editTripEmergencyName ? (elements.editTripEmergencyName.value.trim() || null) : null,
      emergencyContactPhone: normalizedEmergencyPhone,
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

    // Phase 18D Dual Auth Role Switchers & Provider Forms
    const tabAuthTraveller = elements.tabAuthTraveller || document.getElementById('tab-auth-traveller');
    if (tabAuthTraveller) tabAuthTraveller.addEventListener('click', () => switchAuthRole('TRAVELLER'));

    const tabAuthProvider = elements.tabAuthProvider || document.getElementById('tab-auth-provider');
    if (tabAuthProvider) tabAuthProvider.addEventListener('click', () => switchAuthRole('PROVIDER'));

    const tabProvAuthLogin = elements.tabProvAuthLogin || document.getElementById('tab-prov-auth-login');
    if (tabProvAuthLogin) tabProvAuthLogin.addEventListener('click', () => switchProviderAuthMode('LOGIN'));

    const tabProvAuthRegister = elements.tabProvAuthRegister || document.getElementById('tab-prov-auth-register');
    if (tabProvAuthRegister) tabProvAuthRegister.addEventListener('click', () => switchProviderAuthMode('REGISTER'));

    const linkGotoProvRegister = elements.linkGotoProvRegister || document.getElementById('link-goto-prov-register');
    if (linkGotoProvRegister) linkGotoProvRegister.addEventListener('click', () => switchProviderAuthMode('REGISTER'));

    const linkGotoProvLoginFromReg = elements.linkGotoProvLoginFromReg || document.getElementById('link-goto-prov-login-from-reg');
    if (linkGotoProvLoginFromReg) linkGotoProvLoginFromReg.addEventListener('click', () => switchProviderAuthMode('LOGIN'));

    const formProviderLogin = elements.formProviderLogin || document.getElementById('form-provider-login');
    if (formProviderLogin) formProviderLogin.addEventListener('submit', handleProviderLogin);

    const formProviderSignup = elements.formProviderSignup || document.getElementById('form-provider-signup');
    if (formProviderSignup) formProviderSignup.addEventListener('submit', handleProviderSignup);

    const tabRegTraveller = elements.tabRegTraveller || document.getElementById('tab-reg-traveller');
    if (tabRegTraveller) tabRegTraveller.addEventListener('click', () => {
      closeRegisterModal();
      openLoginModal('TRAVELLER');
    });

    const tabRegProvider = elements.tabRegProvider || document.getElementById('tab-reg-provider');
    if (tabRegProvider) tabRegProvider.addEventListener('click', () => {
      closeRegisterModal();
      openLoginModal('PROVIDER');
      switchProviderAuthMode('REGISTER');
    });

    const btnOpenProviderUpgrade = elements.btnOpenProviderUpgrade || document.getElementById('btn-open-provider-upgrade');
    if (btnOpenProviderUpgrade) {
      btnOpenProviderUpgrade.addEventListener('click', () => {
        const modalApply = document.getElementById('modal-provider-apply');
        if (modalApply) modalApply.classList.remove('hidden');
      });
    }

    const btnCloseProvApplyModal = elements.btnCloseProvApplyModal || document.getElementById('btn-close-prov-apply-modal');
    if (btnCloseProvApplyModal) {
      btnCloseProvApplyModal.addEventListener('click', () => {
        const modalApply = document.getElementById('modal-provider-apply');
        if (modalApply) modalApply.classList.add('hidden');
      });
    }

    const formAccountProviderApply = elements.formAccountProviderApply || document.getElementById('form-account-provider-apply');
    if (formAccountProviderApply) formAccountProviderApply.addEventListener('submit', handleAccountProviderApply);

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
    ['modal-login', 'modal-register', 'modal-account', 'modal-provider-apply', 'modal-confirm-booking',
      'modal-seat-selection', 'modal-checkout', 'modal-confirmation', 'modal-cancel-booking'].forEach((id) => {
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
    if (elements.navBtnMarketSearch) elements.navBtnMarketSearch.addEventListener('click', () => switchView('market-search'));
    if (elements.navBtnMarketBookings) elements.navBtnMarketBookings.addEventListener('click', () => switchView('market-bookings'));
    if (elements.navBtnDirectory) elements.navBtnDirectory.addEventListener('click', () => switchView('directory'));
    if (elements.navBtnWorkspace) elements.navBtnWorkspace.addEventListener('click', () => switchView('workspace'));
    if (elements.btnBackToDirectory) elements.btnBackToDirectory.addEventListener('click', () => switchView('directory'));
    if (elements.btnSwitchToSearch) elements.btnSwitchToSearch.addEventListener('click', () => switchView('market-search'));

    // Trip Subnav Bar listeners (Client-Side Routes)
    if (elements.tabTripOverview) {
      elements.tabTripOverview.addEventListener('click', () => {
        if (state.currentTripId) navigateTo(`/trips/${state.currentTripId}`);
      });
    }
    if (elements.tabTripDisruption) {
      elements.tabTripDisruption.addEventListener('click', () => {
        if (state.currentTripId) navigateTo(`/trips/${state.currentTripId}/disruption`);
      });
    }
    if (elements.tabTripRecovery) {
      elements.tabTripRecovery.addEventListener('click', () => {
        if (state.currentTripId) navigateTo(`/trips/${state.currentTripId}/recovery`);
      });
    }
    if (elements.tabTripPlan) {
      elements.tabTripPlan.addEventListener('click', () => {
        if (state.currentTripId) navigateTo(`/trips/${state.currentTripId}/plan`);
      });
    }
    if (elements.tabTripNotifications) {
      elements.tabTripNotifications.addEventListener('click', () => {
        if (state.currentTripId) navigateTo(`/trips/${state.currentTripId}/notifications`);
      });
    }
    if (elements.tabTripLedger) {
      elements.tabTripLedger.addEventListener('click', () => {
        if (state.currentTripId) navigateTo(`/trips/${state.currentTripId}/ledger`);
      });
    }

    // Intercept internal routing links
    document.addEventListener('click', (e) => {
      const link = e.target.closest('a[href^="/"]');
      if (!link) return;
      const href = link.getAttribute('href');
      if (link.getAttribute('target') === '_blank' || link.hasAttribute('download')) return;
      if (href && (href.startsWith('/search') || href.startsWith('/checkout') || href.startsWith('/trips') || href.startsWith('/provider') || href === '/admin')) {
        e.preventDefault();
        navigateTo(href);
      }
    });

    window.addEventListener('popstate', () => {
      handleRoute();
    });

    // Global Action Buttons
    if (elements.btnLoadDemo) elements.btnLoadDemo.addEventListener('click', handleLoadDemo);
    if (elements.btnReset) elements.btnReset.addEventListener('click', handleResetDemo);
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

    // Phase 11: Recovery Progress Tracker Actions (delegated)
    if (elements.trackerActionsList) {
      elements.trackerActionsList.addEventListener('click', (e) => {
        const ledgerBtn = e.target.closest('.btn-goto-ledger-action');
        if (ledgerBtn) {
          const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
          if (tripId) {
            navigateTo(`/trips/${encodeURIComponent(tripId)}/ledger`);
          }
          return;
        }

        const draftHotelBtn = e.target.closest('.btn-tracker-draft-hotel');
        if (draftHotelBtn) {
          const bookingId = draftHotelBtn.getAttribute('data-booking-id');
          prepareAndDraftHotelMessage(bookingId);
          return;
        }

        const btn = e.target.closest('.btn-tracker-action');
        if (!btn || btn.disabled) return;
        const actionId = btn.getAttribute('data-action-id');
        const verb = btn.getAttribute('data-verb');
        const card = btn.closest('.tracker-action-item');
        const noteInput = card ? card.querySelector('.tracker-note-input') : null;
        const note = noteInput ? noteInput.value.trim() : '';
        if (verb === 'skip' && !note) {
          showToast('Please enter a note explaining why this action is skipped.', 'warning');
          if (noteInput) noteInput.focus();
          return;
        }
        handleRecoveryActionMutation(actionId, verb, note, btn);
      });
    }

    // Phase 12: Cost and Refund Ledger events
    const formRecordRefund = document.getElementById('form-record-refund');
    if (formRecordRefund) {
      formRecordRefund.addEventListener('submit', (e) => {
        e.preventDefault();
        handleRecordRefund();
      });
    }
    if (elements.btnSubmitRecordRefund) {
      elements.btnSubmitRecordRefund.addEventListener('click', handleRecordRefund);
    }
    if (elements.btnExportLedgerCsv) {
      elements.btnExportLedgerCsv.addEventListener('click', handleExportLedgerCsv);
    }
    if (elements.btnAddDeductionRow) {
      elements.btnAddDeductionRow.addEventListener('click', () => addDeductionRow());
    }
    if (elements.ledgerRefundAmountInput) {
      elements.ledgerRefundAmountInput.addEventListener('input', updateLiveRefundPreview);
    }
    if (elements.ledgerRefundBookingSelect) {
      elements.ledgerRefundBookingSelect.addEventListener('change', updateLiveRefundPreview);
    }
    if (elements.ledgerTableBody) {
      elements.ledgerTableBody.addEventListener('click', (e) => {
        const btn = e.target.closest('.btn-share-refund');
        if (btn) {
          const entryId = btn.getAttribute('data-entry-id');
          if (entryId) openShareRefundModal(entryId);
        }
      });
    }

    // Phase 12 Add-on: Share Refund Modal events
    if (elements.btnCloseShareModal) {
      elements.btnCloseShareModal.addEventListener('click', closeShareRefundModal);
    }
    if (elements.btnCancelShareRefund) {
      elements.btnCancelShareRefund.addEventListener('click', closeShareRefundModal);
    }
    if (elements.btnConfirmShareRefund) {
      elements.btnConfirmShareRefund.addEventListener('click', handleConfirmShareRefund);
    }
    if (elements.shareRefundChannelSelect) {
      elements.shareRefundChannelSelect.addEventListener('change', updateShareRecipientAndPreview);
    }

    // Phase 13: Communications Assistant events
    if (elements.btnGenerateDraft) {
      elements.btnGenerateDraft.addEventListener('click', handleGenerateDraft);
    }
    if (elements.btnSaveMsgEdits) {
      elements.btnSaveMsgEdits.addEventListener('click', handleSaveMsgEdits);
    }
    if (elements.textareaMsgBody) {
      elements.textareaMsgBody.addEventListener('input', updateMsgCharCounter);
    }
    if (elements.btnSendMessageOpen) {
      elements.btnSendMessageOpen.addEventListener('click', () => {
        if (elements.msgSendConfirmBox) {
          elements.msgSendConfirmBox.classList.remove('hidden');
        }
      });
    }
    if (elements.btnCancelSendMessage) {
      elements.btnCancelSendMessage.addEventListener('click', () => {
        if (elements.msgSendConfirmBox) {
          elements.msgSendConfirmBox.classList.add('hidden');
        }
      });
    }
    if (elements.btnConfirmSendMessage) {
      elements.btnConfirmSendMessage.addEventListener('click', () => handleSendMessage(true));
    }
    if (elements.messagesTableBody) {
      elements.messagesTableBody.addEventListener('click', (e) => {
        const viewBtn = e.target.closest('.btn-view-message');
        if (viewBtn) {
          const msgId = viewBtn.getAttribute('data-msg-id');
          const found = currentMessagesList.find(m => m.id === msgId);
          if (found) {
            displayActiveMessage(found);
            if (elements.msgActiveEditorContainer) {
              elements.msgActiveEditorContainer.scrollIntoView({ behavior: 'smooth' });
            }
          }
        }
      });
    }

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
    if (elements.btnCheckStatusTop) {
      elements.btnCheckStatusTop.addEventListener('click', handleCheckStatusNow);
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
        const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
        if (tripId) {
          navigateTo(`/trips/${encodeURIComponent(tripId)}/recovery`);
        }
      });
    }
    if (elements.btnPlanGotoRecovery) {
      elements.btnPlanGotoRecovery.addEventListener('click', () => {
        const tripId = state.currentTripId || (state.trip ? state.trip.id : null);
        if (tripId) {
          navigateTo(`/trips/${encodeURIComponent(tripId)}/recovery`);
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
    updateAuthHeader();
  }

  async function checkCurrentUser() {
    try {
      const token = sessionStorage.getItem('odyssey_auth_token');
      // If this specific tab has no active session token, remain cleanly unauthenticated
      if (!token) {
        state.currentUser = null;
        updateAuthHeader();
        setUnauthenticatedState(true);
        handleRoute();
        return;
      }

      const headers = {
        'X-Auth-Token': token,
        'Authorization': 'Bearer ' + token
      };
      const res = await originalFetch('/api/auth/me', { credentials: 'same-origin', headers });
      if (res.ok) {
        const data = await res.json();
        if (data && data.success && data.data) {
          state.currentUser = data.data;
          if (data.data.token) {
            sessionStorage.setItem('odyssey_auth_token', data.data.token);
          }
          updateAuthHeader();
          setUnauthenticatedState(false);
          fetchStatusMode();
          fetchTripsDirectory();
          loadMyMarketBookings();
          handleRoute();
          return;
        }
      }
      sessionStorage.removeItem('odyssey_auth_token');
      state.currentUser = null;
      updateAuthHeader();
      setUnauthenticatedState(true);
      handleRoute();
    } catch (e) {
      state.currentUser = null;
      updateAuthHeader();
      setUnauthenticatedState(true);
      switchView('directory');
    }
  }

  function updateAuthHeader() {
    const user = state.currentUser;
    const isAuth = !!user;
    const role = user ? (user.role || 'TRAVELLER') : null;

    // 1. Auth & Profile Header Buttons
    if (isAuth) {
      if (elements.btnOpenLogin) elements.btnOpenLogin.classList.add('hidden');
      if (elements.btnOpenRegister) elements.btnOpenRegister.classList.add('hidden');
      if (elements.userProfilePill) {
        elements.userProfilePill.classList.remove('hidden');
        if (elements.userDisplayName) {
          const roleBadge = role !== 'TRAVELLER' ? ` [${role}]` : '';
          elements.userDisplayName.textContent = (user.name || 'Account') + roleBadge;
        }
      }
    } else {
      if (elements.btnOpenLogin) elements.btnOpenLogin.classList.remove('hidden');
      if (elements.btnOpenRegister) elements.btnOpenRegister.classList.remove('hidden');
      if (elements.userProfilePill) elements.userProfilePill.classList.add('hidden');
    }

    // 2. Header Trip Actions & Active Trip Indicator (Centralized Single Source of Truth)
    // Providers do not have personal trips - hide trip creation actions and active trip badge
    const headerTripActionsGroup = elements.headerTripActionsGroup || document.getElementById('header-trip-actions-group');
    const headerTripStatusGroup = elements.headerTripStatusGroup || document.getElementById('header-trip-status-group');

    if (role === 'PROVIDER') {
      if (headerTripActionsGroup) headerTripActionsGroup.classList.add('hidden');
      if (headerTripStatusGroup) headerTripStatusGroup.classList.add('hidden');
      if (elements.btnOpenCreateTrip) elements.btnOpenCreateTrip.classList.add('hidden');
      if (elements.tripStatusBadge) elements.tripStatusBadge.classList.add('hidden');
    } else {
      // TRAVELLER, ADMIN, or UNAUTHENTICATED
      if (headerTripActionsGroup) headerTripActionsGroup.classList.remove('hidden');
      if (headerTripStatusGroup) headerTripStatusGroup.classList.remove('hidden');
      if (elements.btnOpenCreateTrip) {
        elements.btnOpenCreateTrip.classList.remove('hidden');
        elements.btnOpenCreateTrip.disabled = !isAuth;
      }
      if (elements.tripStatusBadge) elements.tripStatusBadge.classList.remove('hidden');
    }

    // 3. Application Navigation Tabs (Centralized Single Source of Truth for all roles)
    if (role === 'PROVIDER') {
      if (elements.navBtnMarketSearch) elements.navBtnMarketSearch.classList.add('hidden');
      if (elements.navBtnMarketBookings) elements.navBtnMarketBookings.classList.add('hidden');
      if (elements.navBtnDirectory) elements.navBtnDirectory.classList.add('hidden');
      if (elements.navBtnWorkspace) elements.navBtnWorkspace.classList.add('hidden');
      if (elements.navBtnProvider) elements.navBtnProvider.classList.remove('hidden');
      if (elements.navBtnAdmin) elements.navBtnAdmin.classList.add('hidden');
      if (elements.accountProviderUpgradeSection) elements.accountProviderUpgradeSection.classList.add('hidden');
    } else if (role === 'ADMIN') {
      if (elements.navBtnMarketSearch) elements.navBtnMarketSearch.classList.remove('hidden');
      if (elements.navBtnMarketBookings) elements.navBtnMarketBookings.classList.remove('hidden');
      if (elements.navBtnDirectory) elements.navBtnDirectory.classList.remove('hidden');
      if (elements.navBtnWorkspace) elements.navBtnWorkspace.classList.remove('hidden');
      if (elements.navBtnProvider) elements.navBtnProvider.classList.remove('hidden');
      if (elements.navBtnAdmin) elements.navBtnAdmin.classList.remove('hidden');
      if (elements.accountProviderUpgradeSection) elements.accountProviderUpgradeSection.classList.add('hidden');
    } else if (role === 'TRAVELLER') {
      if (elements.navBtnMarketSearch) elements.navBtnMarketSearch.classList.remove('hidden');
      if (elements.navBtnMarketBookings) elements.navBtnMarketBookings.classList.remove('hidden');
      if (elements.navBtnDirectory) elements.navBtnDirectory.classList.remove('hidden');
      if (elements.navBtnWorkspace) elements.navBtnWorkspace.classList.remove('hidden');
      if (elements.navBtnProvider) elements.navBtnProvider.classList.add('hidden');
      if (elements.navBtnAdmin) elements.navBtnAdmin.classList.add('hidden');
      if (elements.accountProviderUpgradeSection) elements.accountProviderUpgradeSection.classList.remove('hidden');
    } else {
      // UNAUTHENTICATED
      if (elements.navBtnMarketSearch) elements.navBtnMarketSearch.classList.add('hidden');
      if (elements.navBtnMarketBookings) elements.navBtnMarketBookings.classList.add('hidden');
      if (elements.navBtnDirectory) elements.navBtnDirectory.classList.remove('hidden');
      if (elements.navBtnWorkspace) elements.navBtnWorkspace.classList.remove('hidden');
      if (elements.navBtnProvider) elements.navBtnProvider.classList.add('hidden');
      if (elements.navBtnAdmin) elements.navBtnAdmin.classList.add('hidden');
      if (elements.accountProviderUpgradeSection) elements.accountProviderUpgradeSection.classList.add('hidden');
    }

    // 4. Directory Panel State
    const panel = elements.panelAuthRequired || document.getElementById('panel-auth-required');
    const dirList = elements.tripsDirectoryList || document.getElementById('trips-directory-list');
    const dirSummary = document.querySelector('.directory-summary-bar');

    if (!isAuth) {
      if (panel) panel.classList.remove('hidden');
      if (dirList) dirList.classList.add('hidden');
      if (dirSummary) dirSummary.classList.add('hidden');
      if (elements.btnDirNewTrip) elements.btnDirNewTrip.disabled = true;
      if (elements.btnRefreshTrips) elements.btnRefreshTrips.disabled = true;
    } else {
      if (panel) panel.classList.add('hidden');
      if (dirList) dirList.classList.remove('hidden');
      if (dirSummary) dirSummary.classList.remove('hidden');
      if (elements.btnDirNewTrip) elements.btnDirNewTrip.disabled = (role === 'PROVIDER');
      if (elements.btnRefreshTrips) elements.btnRefreshTrips.disabled = false;
    }
  }

  function openLoginModal(defaultRole = 'TRAVELLER') {
    if (elements.errLogin) {
      elements.errLogin.textContent = '';
      elements.errLogin.classList.add('hidden');
    }
    if (elements.errProviderLogin) {
      elements.errProviderLogin.textContent = '';
      elements.errProviderLogin.classList.add('hidden');
    }
    if (elements.errProviderSignup) {
      elements.errProviderSignup.textContent = '';
      elements.errProviderSignup.classList.add('hidden');
    }
    if (elements.formLogin) elements.formLogin.reset();
    if (elements.formProviderLogin) elements.formProviderLogin.reset();
    if (elements.formProviderSignup) elements.formProviderSignup.reset();

    switchAuthRole(defaultRole);

    if (elements.modalLogin) {
      elements.modalLogin.classList.remove('hidden');
      elements.modalLogin.classList.add('is-open');
    }
    const emailInput = defaultRole === 'PROVIDER' ? elements.provLoginEmail : document.getElementById('login-email');
    if (emailInput) setTimeout(() => emailInput.focus(), 50);
  }

  function switchAuthRole(role) {
    if (role === 'PROVIDER') {
      if (elements.tabAuthTraveller) elements.tabAuthTraveller.classList.remove('active');
      if (elements.tabAuthProvider) elements.tabAuthProvider.classList.add('active');
      if (elements.authSectionTraveller) elements.authSectionTraveller.classList.add('hidden');
      if (elements.authSectionProvider) elements.authSectionProvider.classList.remove('hidden');
      switchProviderAuthMode('LOGIN');
    } else {
      if (elements.tabAuthTraveller) elements.tabAuthTraveller.classList.add('active');
      if (elements.tabAuthProvider) elements.tabAuthProvider.classList.remove('active');
      if (elements.authSectionTraveller) elements.authSectionTraveller.classList.remove('hidden');
      if (elements.authSectionProvider) elements.authSectionProvider.classList.add('hidden');
    }
  }

  function switchProviderAuthMode(mode) {
    if (mode === 'REGISTER') {
      if (elements.tabProvAuthLogin) elements.tabProvAuthLogin.classList.remove('active');
      if (elements.tabProvAuthRegister) elements.tabProvAuthRegister.classList.add('active');
      if (elements.formProviderLogin) elements.formProviderLogin.classList.add('hidden');
      if (elements.formProviderSignup) elements.formProviderSignup.classList.remove('hidden');
    } else {
      if (elements.tabProvAuthLogin) elements.tabProvAuthLogin.classList.add('active');
      if (elements.tabProvAuthRegister) elements.tabProvAuthRegister.classList.remove('active');
      if (elements.formProviderLogin) elements.formProviderLogin.classList.remove('hidden');
      if (elements.formProviderSignup) elements.formProviderSignup.classList.add('hidden');
    }
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
      let data = null;
      try {
        data = await res.json();
      } catch (_) { }

      if (!res.ok || !data || !data.success) {
        throw new Error((data && (data.message || data.error)) || 'Invalid email or password');
      }

      state.currentUser = data.data;
      if (data.data && data.data.token) {
        sessionStorage.setItem('odyssey_auth_token', data.data.token);
      }
      updateAuthHeader();
      setUnauthenticatedState(false);
      closeLoginModal();
      showToast('Welcome back, ' + (state.currentUser.name || 'traveller') + '!', 'success');

      fetchStatusMode();
      await fetchTripsDirectory();
      loadMyMarketBookings();

      // Post-login routing by role (Phase 18D)
      if (state.currentUser.role === 'ADMIN') {
        navigateTo('/admin');
      } else if (state.currentUser.role === 'PROVIDER') {
        navigateTo('/provider');
      } else {
        if (state.loginReturnTarget && state.loginReturnTarget.view === 'workspace' && state.loginReturnTarget.tripId) {
          const returnTripId = state.loginReturnTarget.tripId;
          state.loginReturnTarget = null;
          switchView('workspace');
          await loadTripById(returnTripId);
        } else if (state.tripsList && state.tripsList.length > 0) {
          state.loginReturnTarget = null;
          navigateTo('/trips');
        } else {
          state.loginReturnTarget = null;
          navigateTo('/search');
        }
      }
    } catch (err) {
      if (elements.errLogin) {
        elements.errLogin.textContent = err.message;
        elements.errLogin.classList.remove('hidden');
      }
      showToast(err.message, 'error');
    } finally {
      setButtonLoading(elements.btnSubmitLogin, false);
    }
  }

  async function handleProviderLogin(e) {
    e.preventDefault();
    const email = elements.provLoginEmail ? elements.provLoginEmail.value.trim() : '';
    const password = elements.provLoginPassword ? elements.provLoginPassword.value : '';
    if (!email || !password) return;

    setButtonLoading(elements.btnSubmitProviderLogin, true);
    if (elements.errProviderLogin) {
      elements.errProviderLogin.textContent = '';
      elements.errProviderLogin.classList.add('hidden');
    }

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      let data = null;
      try {
        data = await res.json();
      } catch (_) { }

      if (!res.ok || !data || !data.success) {
        throw new Error((data && (data.message || data.error)) || 'Invalid email or password');
      }

      state.currentUser = data.data;
      if (data.data && data.data.token) {
        sessionStorage.setItem('odyssey_auth_token', data.data.token);
      }
      updateAuthHeader();
      setUnauthenticatedState(false);
      closeLoginModal();
      showToast('Welcome back, ' + (state.currentUser.name || 'Provider') + '!', 'success');

      if (state.currentUser.role === 'PROVIDER') {
        navigateTo('/provider');
      } else if (state.currentUser.role === 'ADMIN') {
        navigateTo('/admin');
      } else {
        navigateTo('/search');
      }
    } catch (err) {
      if (elements.errProviderLogin) {
        elements.errProviderLogin.textContent = err.message;
        elements.errProviderLogin.classList.remove('hidden');
      }
      showToast(err.message, 'error');
    } finally {
      setButtonLoading(elements.btnSubmitProviderLogin, false);
    }
  }

  async function handleProviderSignup(e) {
    e.preventDefault();
    const name = elements.provSignupName ? elements.provSignupName.value.trim() : '';
    const phone = elements.provSignupPhone ? elements.provSignupPhone.value.trim() : '';
    const email = elements.provSignupEmail ? elements.provSignupEmail.value.trim() : '';
    const password = elements.provSignupPassword ? elements.provSignupPassword.value : '';
    const businessName = elements.provSignupBizName ? elements.provSignupBizName.value.trim() : '';
    const businessType = elements.provSignupBizType ? elements.provSignupBizType.value : '';
    const city = elements.provSignupCity ? elements.provSignupCity.value.trim() : '';
    const description = elements.provSignupDesc ? elements.provSignupDesc.value.trim() : '';

    if (!name || !email || !password || !businessName || !businessType || !phone || !city) {
      showToast('Please fill in all required fields.', 'warning');
      return;
    }

    setButtonLoading(elements.btnSubmitProviderSignup, true);
    if (elements.errProviderSignup) {
      elements.errProviderSignup.innerHTML = '';
      elements.errProviderSignup.classList.add('hidden');
    }

    try {
      const res = await fetch('/api/auth/provider-register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name,
          email,
          password,
          businessName,
          businessType,
          contactPhone: phone,
          contactEmail: email,
          city,
          description
        })
      });

      let data = null;
      try {
        data = await res.json();
      } catch (_) { }

      if (!res.ok) {
        if (res.status === 409) {
          const conflictMsg = 'An account with this email already exists. Try logging in instead, or use a different email.';
          if (elements.errProviderSignup) {
            elements.errProviderSignup.innerHTML = `
              <span>An account with this email already exists. Try <button type="button" id="btn-err-switch-prov-login" style="color: var(--gold-light); background: none; border: none; padding: 0; cursor: pointer; text-decoration: underline; font-weight: 600;">logging in instead</button>, or use a different email.</span>
            `;
            elements.errProviderSignup.classList.remove('hidden');
            const switchBtn = elements.errProviderSignup.querySelector('#btn-err-switch-prov-login');
            if (switchBtn) {
              switchBtn.addEventListener('click', () => {
                if (elements.provLoginEmail && email) {
                  elements.provLoginEmail.value = email;
                }
                switchProviderAuthMode('LOGIN');
                if (elements.provLoginPassword) elements.provLoginPassword.focus();
              });
            }
          }
          showToast(conflictMsg, 'warning');
          return;
        }

        const errMsg = (data && (data.message || data.error)) || `Provider registration failed (Status ${res.status})`;
        if (elements.errProviderSignup) {
          elements.errProviderSignup.textContent = errMsg;
          elements.errProviderSignup.classList.remove('hidden');
        }
        showToast(errMsg, 'error');
        return;
      }

      if (!data || !data.success) {
        const errMsg = (data && data.message) || 'Provider registration failed';
        if (elements.errProviderSignup) {
          elements.errProviderSignup.textContent = errMsg;
          elements.errProviderSignup.classList.remove('hidden');
        }
        showToast(errMsg, 'error');
        return;
      }

      state.currentUser = data.data;
      if (data.data && data.data.token) {
        sessionStorage.setItem('odyssey_auth_token', data.data.token);
      }
      updateAuthHeader();
      setUnauthenticatedState(false);
      closeLoginModal();
      showToast('Provider registered! Status is PENDING review.', 'success');

      navigateTo('/provider');
    } catch (err) {
      if (elements.errProviderSignup) {
        elements.errProviderSignup.textContent = err.message || 'An unexpected error occurred';
        elements.errProviderSignup.classList.remove('hidden');
      }
      showToast(err.message || 'An unexpected error occurred', 'error');
    } finally {
      setButtonLoading(elements.btnSubmitProviderSignup, false);
    }
  }

  async function handleAccountProviderApply(e) {
    e.preventDefault();
    const businessName = document.getElementById('apply-biz-name') ? document.getElementById('apply-biz-name').value.trim() : '';
    const businessType = document.getElementById('apply-biz-type') ? document.getElementById('apply-biz-type').value : '';
    const city = document.getElementById('apply-city') ? document.getElementById('apply-city').value.trim() : '';
    const contactPhone = document.getElementById('apply-phone') ? document.getElementById('apply-phone').value.trim() : '';
    const contactEmail = document.getElementById('apply-email') ? document.getElementById('apply-email').value.trim() : '';
    const description = document.getElementById('apply-desc') ? document.getElementById('apply-desc').value.trim() : '';

    if (!businessName || !businessType || !city || !contactPhone || !contactEmail) {
      showToast('Please fill in all required fields.', 'warning');
      return;
    }

    const submitBtn = document.getElementById('btn-submit-account-provider-apply');
    setButtonLoading(submitBtn, true);
    const errBox = document.getElementById('err-account-provider-apply');
    if (errBox) {
      errBox.textContent = '';
      errBox.classList.add('hidden');
    }

    try {
      const res = await fetch('/api/provider/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ businessName, businessType, city, contactPhone, contactEmail, description }),
      });
      let data = null;
      try {
        data = await res.json();
      } catch (_) { }

      if (!res.ok || !data || !data.success) {
        throw new Error((data && (data.message || data.error)) || 'Provider application failed');
      }

      if (state.currentUser) {
        state.currentUser.role = 'PROVIDER';
        updateAuthHeader();
      }
      const modalApply = document.getElementById('modal-provider-apply');
      if (modalApply) modalApply.classList.add('hidden');
      closeAccountModal();
      showToast('Provider application submitted! Status is PENDING review.', 'success');
      navigateTo('/provider');
    } catch (err) {
      if (errBox) {
        errBox.textContent = err.message;
        errBox.classList.remove('hidden');
      }
      showToast(err.message, 'error');
    } finally {
      setButtonLoading(submitBtn, false);
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
      elements.errRegister.innerHTML = '';
      elements.errRegister.classList.add('hidden');
    }

    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, password, phone }),
      });
      let data = null;
      try {
        data = await res.json();
      } catch (_) { }

      if (!res.ok) {
        if (res.status === 409) {
          const conflictMsg = 'An account with this email already exists. Try logging in instead, or use a different email.';
          if (elements.errRegister) {
            elements.errRegister.innerHTML = `
              <span>An account with this email already exists. Try <button type="button" id="btn-err-switch-trav-login" style="color: var(--gold-light); background: none; border: none; padding: 0; cursor: pointer; text-decoration: underline; font-weight: 600;">logging in instead</button>, or use a different email.</span>
            `;
            elements.errRegister.classList.remove('hidden');
            const switchBtn = elements.errRegister.querySelector('#btn-err-switch-trav-login');
            if (switchBtn) {
              switchBtn.addEventListener('click', () => {
                closeRegisterModal();
                openLoginModal('TRAVELLER');
                const loginEmail = document.getElementById('login-email');
                if (loginEmail && email) loginEmail.value = email;
              });
            }
          }
          showToast(conflictMsg, 'warning');
          return;
        }

        const errMsg = (data && (data.message || data.error)) || `Registration failed (Status ${res.status})`;
        if (elements.errRegister) {
          elements.errRegister.textContent = errMsg;
          elements.errRegister.classList.remove('hidden');
        }
        showToast(errMsg, 'error');
        return;
      }

      if (!data || !data.success) {
        throw new Error((data && data.message) || 'Registration failed');
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
          if (loginData.data && loginData.data.token) {
            sessionStorage.setItem('odyssey_auth_token', loginData.data.token);
          }
        } else {
          state.currentUser = data.data;
          if (data.data && data.data.token) {
            sessionStorage.setItem('odyssey_auth_token', data.data.token);
          }
        }
      } catch (errLogin) {
        state.currentUser = data.data;
        if (data.data && data.data.token) {
          sessionStorage.setItem('odyssey_auth_token', data.data.token);
        }
      }

      updateAuthHeader();
      setUnauthenticatedState(false);
      closeRegisterModal();
      showToast('Account created successfully! Welcome to Odyssey.', 'success');
      fetchStatusMode();
      loadMyMarketBookings();
      switchView('market-search');
    } catch (err) {
      if (elements.errRegister) {
        elements.errRegister.textContent = err.message;
        elements.errRegister.classList.remove('hidden');
      }
      showToast(err.message, 'error');
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
      sessionStorage.removeItem('odyssey_auth_token');
      setButtonLoading(elements.btnLogout, false);
      state.currentUser = null;
      state.tripsList = [];
      state.currentTrip = null;
      state.currentTripId = null;
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
  // Phase 10A: Marketplace In-App Booking Logic
  // ==========================================

  const marketState = {
    services: [],
    hotels: [],
    cabs: [],
    currentService: null,
    selectedSeats: new Set(),
    flightPassengerCount: 1,
    bookingToCancelId: null,
    bookingToCancelRef: null,
    checkoutType: 'TRANSPORT',
    pendingHotel: null,
    pendingCab: null,
  };

  function initMarketplaceDates() {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const tomorrowStr = tomorrow.toISOString().substring(0, 10);

    const dayAfter = new Date();
    dayAfter.setDate(dayAfter.getDate() + 2);
    const dayAfterStr = dayAfter.toISOString().substring(0, 10);

    if (elements.marketSearchDate) elements.marketSearchDate.value = tomorrowStr;
    if (elements.marketHotelCheckin) elements.marketHotelCheckin.value = tomorrowStr;
    if (elements.marketHotelCheckout) elements.marketHotelCheckout.value = dayAfterStr;
  }

  // Subtab Switching (Transport / Hotels / Cabs)
  function setupMarketSubtabs() {
    const tabs = [
      { btn: elements.tabMarketTransport, panel: elements.subpanelMarketTransport },
      { btn: elements.tabMarketHotels, panel: elements.subpanelMarketHotels },
      { btn: elements.tabMarketCabs, panel: elements.subpanelMarketCabs },
    ];

    tabs.forEach(({ btn, panel }) => {
      if (!btn) return;
      btn.addEventListener('click', () => {
        tabs.forEach(t => {
          if (t.btn) t.btn.classList.remove('active');
          if (t.panel) t.panel.classList.add('hidden');
        });
        btn.classList.add('active');
        if (panel) panel.classList.remove('hidden');
      });
    });
  }

  // Transport Search Handler
  async function handleMarketSearch(e) {
    if (e) e.preventDefault();

    if (!state.currentUser) {
      showToast('Please sign in to search and book travel services.', 'warning');
      openLoginModal();
      return;
    }

    const origin = elements.marketSearchOrigin.value.trim();
    const destination = elements.marketSearchDest.value.trim();
    const date = elements.marketSearchDate.value;
    const mode = elements.marketSearchMode.value;

    if (!origin || !destination) {
      showToast('Please enter both origin and destination.', 'warning');
      return;
    }

    setButtonLoading(elements.btnMarketSearch, true);
    elements.marketServicesCount.textContent = 'Searching departures...';
    elements.marketServicesList.innerHTML = '<div class="market-empty-state"><p>Querying verified departure catalogue...</p></div>';

    try {
      const queryParams = new URLSearchParams({
        origin,
        destination,
        date: date || '',
        mode: mode || 'ALL',
      });

      const res = await fetch(`/api/market/services?${queryParams.toString()}`);
      const data = await res.json();

      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to search departures');
      }

      const services = data.data || [];
      marketState.services = services;
      renderMarketServices(services);
    } catch (err) {
      elements.marketServicesCount.textContent = 'Search failed';
      elements.marketServicesList.innerHTML = `<div class="market-empty-state"><p style="color: #f87171;">${escapeHtml(err.message)}</p></div>`;
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnMarketSearch, false);
    }
  }

  function renderMarketServices(services) {
    if (!elements.marketServicesList) return;

    elements.marketServicesCount.textContent = `${services.length} departure${services.length === 1 ? '' : 's'} available`;

    if (services.length === 0) {
      elements.marketServicesList.innerHTML = `
          <div class="market-empty-state">
            <p>No scheduled departures found for this route and date.</p>
          </div>
        `;
      return;
    }

    elements.marketServicesList.innerHTML = '';
    services.forEach((s) => {
      const card = document.createElement('div');
      card.className = 'market-service-card';

      const modeBadgeClass = s.mode === 'BUS' ? 'badge-amber' : (s.mode === 'TRAIN' ? 'badge-blue' : 'badge-purple');
      const priceFormatted = Number(s.price).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

      const isProvider = s.source === 'PROVIDER';
      const sourceBadgeClass = isProvider ? 'badge-provider' : (s.source === 'GENERATED' ? 'badge-generated' : 'badge-seeded');
      const sourceBadgeText = isProvider ? `Provider-listed • ${s.operator}` : (s.sourceLabel || (s.source === 'GENERATED' ? 'Generated schedule' : 'Seeded schedule'));
      const scheduleNotice = isProvider
        ? `<div class="schedule-disclaimer-notice">⚠️ Provider-listed • Odyssey does not verify schedules or availability.</div>`
        : (s.scheduleLabel ? `<div class="schedule-disclaimer-notice">⚠️ ${escapeHtml(s.scheduleLabel)}</div>` : '');

      card.innerHTML = `
          <div class="service-card-top">
            <div class="service-op-group">
              <span class="badge ${modeBadgeClass}">${escapeHtml(s.mode)}</span>
              <span class="service-operator-name">${escapeHtml(s.operator)}</span>
              <span class="service-vehicle-number">${escapeHtml(s.vehicleNumber)}</span>
            </div>
            <div class="service-badges">
              <span class="badge ${sourceBadgeClass}">${escapeHtml(sourceBadgeText)}</span>
              <span class="badge badge-green">${s.reliabilityScore}% Reliability</span>
              <span class="badge ${s.seatsLeft > 5 ? 'badge-neutral' : 'badge-amber'}">${s.seatsLeft} seat${s.seatsLeft === 1 ? '' : 's'} left</span>
            </div>
          </div>

          <div class="service-timing-grid">
            <div class="timing-node">
              <span class="timing-time">${escapeHtml(String(s.departureTime).substring(0, 5))}</span>
              <span class="timing-city">${escapeHtml(s.originCity)}</span>
              <span class="timing-stop" title="${escapeHtml(s.originStop)}">${escapeHtml(s.originStop)}</span>
            </div>
            <div class="timing-duration-mid">
              <span class="duration-chip">${escapeHtml(s.duration)}</span>
              <span class="duration-arrow">&rarr;</span>
            </div>
            <div class="timing-node dest">
              <span class="timing-time">${escapeHtml(String(s.arrivalTime).substring(0, 5))}</span>
              <span class="timing-city">${escapeHtml(s.destinationCity)}</span>
              <span class="timing-stop" title="${escapeHtml(s.destinationStop)}">${escapeHtml(s.destinationStop)}</span>
            </div>
          </div>

          ${scheduleNotice}

          <div class="service-card-bottom">
            <div class="service-price-group">
              <span style="color: #94a3b8; font-size: 0.85rem;">Fare per seat:</span>
              <span class="service-price">&#8377;${priceFormatted}</span>
            </div>
            <div style="display: flex; gap: 8px; align-items: center;">
              ${state.statusMode !== 'LIVE' ? `
                <button type="button" class="btn btn-outline btn-sm btn-simulate-departure" data-departure-id="${escapeHtml(s.departureId)}" data-operator="${escapeHtml(s.operator)}" data-vehicle="${escapeHtml(s.vehicleNumber)}" data-status="${escapeHtml(s.status || 'SCHEDULED')}" data-delay="${s.delayMinutes || 0}">
                  Simulated status
                </button>
              ` : ''}
              <button type="button" class="btn btn-gold btn-book-service" data-departure-id="${escapeHtml(s.departureId)}" ${s.bookable ? '' : 'disabled'}>
                ${s.bookable ? 'Select Seats & Book &rarr;' : 'Sold Out / Past'}
              </button>
            </div>
          </div>
        `;

      elements.marketServicesList.appendChild(card);
    });
  }

  // Seat Selection Modal
  function openSeatSelectionModal(service) {
    if (!state.currentUser) {
      showToast('Please sign in to book.', 'warning');
      openLoginModal();
      return;
    }

    marketState.currentService = service;
    marketState.selectedSeats.clear();
    marketState.flightPassengerCount = 1;

    // Summary Header
    const priceFormatted = Number(service.price).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    elements.seatModalServiceSummary.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem;">
          <div>
            <strong style="color: #f8fafc; font-size: 1.1rem;">${escapeHtml(service.operator)} (${escapeHtml(service.vehicleNumber)})</strong>
            <div style="font-size: 0.85rem; color: #94a3b8; margin-top: 2px;">
              ${escapeHtml(service.originCity)} &rarr; ${escapeHtml(service.destinationCity)} &bull; ${escapeHtml(String(service.departureTime).substring(0, 5))} &bull; &#8377;${priceFormatted} / seat
            </div>
          </div>
          <div>
            <span class="badge badge-info">${service.seatsLeft} seats available</span>
          </div>
        </div>
      `;

    if (service.mode === 'FLIGHT') {
      elements.seatGrid.classList.add('hidden');
      elements.flightSeatSelector.classList.remove('hidden');
      elements.flightPassengerCount.value = '1';
      renderPassengerInputs(['Seat 1 (Auto)']);
    } else {
      elements.seatGrid.classList.remove('hidden');
      elements.flightSeatSelector.classList.add('hidden');
      renderSeatGrid(service);
      renderPassengerInputs([]);
    }

    elements.modalSeatSelection.classList.remove('hidden');
    elements.modalSeatSelection.classList.add('is-open');
  }

  function closeSeatSelectionModal() {
    if (elements.modalSeatSelection) {
      elements.modalSeatSelection.classList.add('hidden');
      elements.modalSeatSelection.classList.remove('is-open');
    }
  }

  function renderSeatGrid(service) {
    if (!elements.seatGrid) return;
    elements.seatGrid.innerHTML = '';

    const totalSeats = service.seatsTotal || 40;
    const seatsAvailable = service.seatsLeft;
    const takenCount = Math.max(0, totalSeats - seatsAvailable);

    // Deterministically mark the first N taken seats as occupied
    for (let i = 1; i <= Math.min(totalSeats, 40); i++) {
      const row = Math.ceil(i / 4);
      const colLetter = ['A', 'B', 'C', 'D'][(i - 1) % 4];
      const seatNum = `${row}${colLetter}`;

      const isTaken = i <= takenCount;
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `seat-btn ${isTaken ? 'seat-taken' : ''}`;
      btn.textContent = seatNum;
      btn.disabled = isTaken;
      btn.setAttribute('data-seat', seatNum);

      if (!isTaken) {
        btn.addEventListener('click', () => toggleSeatSelection(seatNum, btn));
      }

      elements.seatGrid.appendChild(btn);
    }
  }

  function toggleSeatSelection(seatNum, btn) {
    if (marketState.selectedSeats.has(seatNum)) {
      marketState.selectedSeats.delete(seatNum);
      btn.classList.remove('seat-selected');
    } else {
      if (marketState.selectedSeats.size >= 6) {
        showToast('Maximum 6 passengers allowed per booking.', 'warning');
        return;
      }
      marketState.selectedSeats.add(seatNum);
      btn.classList.add('seat-selected');
    }
    renderPassengerInputs(Array.from(marketState.selectedSeats));
  }

  function renderPassengerInputs(seatList) {
    if (!elements.passengerInputsContainer) return;
    elements.passengerInputsContainer.innerHTML = '';

    if (seatList.length === 0) {
      elements.passengerInputsContainer.innerHTML = '<p style="color: #94a3b8; font-size: 0.85rem; font-style: italic;">Click seats above to add passenger details.</p>';
      return;
    }

    seatList.forEach((seat, idx) => {
      const row = document.createElement('div');
      row.className = 'passenger-input-row';

      const defaultName = (idx === 0 && state.currentUser && state.currentUser.name) ? state.currentUser.name : '';

      row.innerHTML = `
          <div>
            <span class="seat-badge">${escapeHtml(seat)}</span>
          </div>
          <div>
            <input type="text" class="form-control passenger-name-input" placeholder="Full Name" value="${escapeHtml(defaultName)}" required>
          </div>
          <div>
            <input type="number" class="form-control passenger-age-input" placeholder="Age" min="1" max="120" value="28" required>
          </div>
        `;

      elements.passengerInputsContainer.appendChild(row);
    });
  }

  function getDestinationBusStopOrLandmark(service) {
    if (!service) return 'City Bus Stand';
    const destCity = (service.destinationCity || '').trim();
    const origCity = (service.originCity || '').trim();

    // If destinationStop exists and doesn't mistakenly reference the origin city
    if (service.destinationStop &&
      (!origCity || !service.destinationStop.toLowerCase().includes(origCity.toLowerCase())) &&
      (!service.destinationStop.toLowerCase().includes('origin'))) {
      return service.destinationStop;
    }

    const cityUpper = destCity.toUpperCase();
    const famousStops = {
      'MYSURU': 'Suburban Bus Stand, Mysuru',
      'MYSORE': 'Suburban Bus Stand, Mysuru',
      'BENGALURU': 'Kempegowda Bus Station (Majestic), Bengaluru',
      'BANGALORE': 'Kempegowda Bus Station (Majestic), Bengaluru',
      'HUBBALLI': 'New Bus Stand, Gokul Road, Hubballi',
      'HUBLI': 'New Bus Stand, Gokul Road, Hubballi',
      'GOA': 'Kadamba Bus Stand, Panaji',
      'MANGALURU': 'KSRTC Bus Stand (Bejai), Mangaluru',
      'MANGALORE': 'KSRTC Bus Stand (Bejai), Mangaluru',
      'UDUPI': 'Service Bus Stand, Udupi',
      'MUMBAI': 'Dadar Central Bus Stand, Mumbai',
      'PUNE': 'Swargate Bus Stand, Pune',
      'CHENNAI': 'Koyambedu Bus Stand (CMBT), Chennai',
      'HYDERABAD': 'MGBS Central Bus Station, Hyderabad',
      'SIRSI': 'Old Bus Stand, Sirsi'
    };

    if (famousStops[cityUpper]) {
      return famousStops[cityUpper];
    }

    if (service.mode === 'TRAIN') return `${destCity} Railway Station`;
    if (service.mode === 'FLIGHT') return `${destCity} Airport`;
    return `${destCity} Central Bus Stand`;
  }

  // Checkout Modal
  function openCheckoutModal() {
    const service = marketState.currentService;
    if (!service) return;

    const isFlight = service.mode === 'FLIGHT';
    let passengers = [];

    const nameInputs = elements.passengerInputsContainer.querySelectorAll('.passenger-name-input');
    const ageInputs = elements.passengerInputsContainer.querySelectorAll('.passenger-age-input');

    if (isFlight) {
      const count = parseInt(elements.flightPassengerCount.value, 10) || 1;
      if (count < 1 || count > 6) {
        showToast('Please select between 1 and 6 passengers', 'warning');
        return;
      }
      for (let i = 0; i < count; i++) {
        const nameInput = nameInputs[i];
        const ageInput = ageInputs[i];
        const name = nameInput ? nameInput.value.trim() : (i === 0 && state.currentUser ? state.currentUser.name : `Passenger ${i + 1}`);
        const age = ageInput ? parseInt(ageInput.value, 10) || 28 : 28;
        if (!name) {
          showToast(`Please enter name for Passenger ${i + 1}`, 'warning');
          return;
        }
        passengers.push({ name, age, seatNumber: String(i + 1) });
      }
    } else {
      const seats = Array.from(marketState.selectedSeats);
      if (seats.length === 0) {
        showToast('Please select at least one seat', 'warning');
        return;
      }
      for (let i = 0; i < seats.length; i++) {
        const nameInput = nameInputs[i];
        const ageInput = ageInputs[i];
        const name = nameInput ? nameInput.value.trim() : '';
        const age = ageInput ? parseInt(ageInput.value, 10) || 28 : 28;
        if (!name) {
          showToast(`Please enter passenger name for seat ${seats[i]}`, 'warning');
          return;
        }
        passengers.push({ name, age, seatNumber: seats[i] });
      }
    }

    marketState.checkoutType = 'TRANSPORT';
    marketState.passengers = passengers;
    const totalAmount = Number(service.price) * passengers.length;
    const formattedTotal = totalAmount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    elements.checkoutOrderSummary.innerHTML = `
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Service / Route:</span>
          <strong>${escapeHtml(service.operator)} (${escapeHtml(service.vehicleNumber)})</strong>
        </div>
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Route:</span>
          <span>${escapeHtml(service.originCity)} &rarr; ${escapeHtml(service.destinationCity)}</span>
        </div>
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Departure:</span>
          <span>${escapeHtml(String(service.departureTime).substring(0, 5))}</span>
        </div>
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Passengers:</span>
          <span>${passengers.map(p => escapeHtml(p.name + ' [' + p.seatNumber + ']')).join(', ')}</span>
        </div>
        <div style="display: flex; justify-content: space-between; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 0.5rem; margin-top: 0.5rem; font-size: 1.1rem;">
          <span style="color: #f8fafc; font-weight: 700;">Total Demo Fare:</span>
          <span style="color: #4ade80; font-weight: 800;">&#8377;${formattedTotal}</span>
        </div>
      `;

    populateCheckoutTripOptions();

    elements.btnPayNowText.textContent = `Pay \u20B9${formattedTotal}`;
    elements.cardHolderName.value = (state.currentUser && state.currentUser.name) ? state.currentUser.name : '';
    elements.cardNumber.value = '4111 2222 3333 4444';
    elements.cardExpiry.value = '12/28';
    elements.cardCvv.value = '123';
    elements.checkoutErrorMsg.classList.add('hidden');
    elements.checkoutErrorMsg.textContent = '';

    closeSeatSelectionModal();
    // Show and configure Add-ons section for transport checkout
    const addonsSec = document.getElementById('checkout-addons-section');
    if (addonsSec) addonsSec.classList.remove('hidden');
    if (elements.checkoutAddonCabToggle) elements.checkoutAddonCabToggle.checked = false;
    if (elements.checkoutAddonCabContainer) elements.checkoutAddonCabContainer.classList.add('hidden');
    const destStop = getDestinationBusStopOrLandmark(service);
    if (elements.checkoutCabPickup) elements.checkoutCabPickup.value = destStop;
    if (elements.checkoutCabDropoff) elements.checkoutCabDropoff.value = `${service.destinationCity} Center`;
    if (elements.checkoutSelectedCabId) elements.checkoutSelectedCabId.value = '';
    if (elements.checkoutCabOptionsList) elements.checkoutCabOptionsList.innerHTML = '<div style="font-size: 0.85rem; color: #94a3b8; text-align: center; padding: 0.5rem;">Loading available cabs...</div>';

    if (elements.checkoutAddonHotelToggle) elements.checkoutAddonHotelToggle.checked = false;
    if (elements.checkoutAddonHotelContainer) elements.checkoutAddonHotelContainer.classList.add('hidden');
    if (elements.checkoutHotelName) elements.checkoutHotelName.value = '';
    if (elements.checkoutHotelNights) elements.checkoutHotelNights.value = '1';
    if (elements.checkoutHotelRooms) elements.checkoutHotelRooms.value = '1';
    if (elements.checkoutSelectedHotelId) elements.checkoutSelectedHotelId.value = '';
    if (elements.checkoutSelectedRoomTypeId) elements.checkoutSelectedRoomTypeId.value = '';
    if (elements.checkoutHotelOptionsList) elements.checkoutHotelOptionsList.innerHTML = '<div style="font-size: 0.85rem; color: #94a3b8; text-align: center; padding: 0.5rem;">Loading available hotels...</div>';

    // Reset payment method to CARD
    if (elements.paymentMethodCard) elements.paymentMethodCard.checked = true;
    if (elements.checkoutCardFields) elements.checkoutCardFields.classList.remove('hidden');
    if (elements.checkoutUpiFields) elements.checkoutUpiFields.classList.add('hidden');
    if (elements.checkoutNetbankingFields) elements.checkoutNetbankingFields.classList.add('hidden');
    if (elements.checkoutUpiId) elements.checkoutUpiId.value = '';
    if (elements.checkoutBankSelect) elements.checkoutBankSelect.selectedIndex = 0;

    elements.modalCheckout.classList.remove('hidden');
    elements.modalCheckout.classList.add('is-open');
  }

  function openHotelCheckoutModal(hotel) {
    const addonsSec = document.getElementById('checkout-addons-section');
    if (addonsSec) addonsSec.classList.add('hidden');
    if (!hotel) return;
    marketState.checkoutType = 'HOTEL';
    const checkIn = elements.marketHotelCheckin ? elements.marketHotelCheckin.value : '';
    const checkOut = elements.marketHotelCheckout ? elements.marketHotelCheckout.value : '';
    const rooms = elements.marketHotelRooms ? (parseInt(elements.marketHotelRooms.value, 10) || 1) : 1;
    const guestName = (state.currentUser && state.currentUser.name) ? state.currentUser.name : 'Guest';

    marketState.pendingHotel = {
      hotelId: hotel.id,
      roomTypeId: hotel.roomTypeId,
      hotelName: hotel.name,
      hotelCity: hotel.city,
      checkIn,
      checkOut,
      rooms,
      guestName,
      totalFare: hotel.estimatedTotal
    };

    const formattedTotal = Number(hotel.estimatedTotal).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    elements.checkoutOrderSummary.innerHTML = `
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Hotel / Location:</span>
          <strong>${escapeHtml(hotel.name)} (${escapeHtml(hotel.city)})</strong>
        </div>
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Dates:</span>
          <span>${escapeHtml(checkIn)} &rarr; ${escapeHtml(checkOut)} (${hotel.nights} night(s))</span>
        </div>
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Rooms & Guest:</span>
          <span>${escapeHtml(guestName)} (${rooms} room(s))</span>
        </div>
        <div style="display: flex; justify-content: space-between; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 0.5rem; margin-top: 0.5rem; font-size: 1.1rem;">
          <span style="color: #f8fafc; font-weight: 700;">Total Demo Fare:</span>
          <span style="color: #4ade80; font-weight: 800;">&#8377;${formattedTotal}</span>
        </div>
      `;

    populateCheckoutTripOptions();

    elements.btnPayNowText.textContent = `Pay \u20B9${formattedTotal}`;
    elements.cardHolderName.value = (state.currentUser && state.currentUser.name) ? state.currentUser.name : '';
    elements.cardNumber.value = '4111 2222 3333 4444';
    elements.cardExpiry.value = '12/28';
    elements.cardCvv.value = '123';
    elements.checkoutErrorMsg.classList.add('hidden');
    elements.checkoutErrorMsg.textContent = '';

    elements.modalCheckout.classList.remove('hidden');
    elements.modalCheckout.classList.add('is-open');
  }

  function openCabCheckoutModal(cab) {
    if (!cab) return;
    marketState.checkoutType = 'CAB';
    const pickup = elements.marketCabPickup ? elements.marketCabPickup.value.trim() : '';
    const dropoff = elements.marketCabDropoff ? elements.marketCabDropoff.value.trim() : '';
    const nowTime = new Date().toISOString().substring(0, 19);

    marketState.pendingCab = {
      cabServiceId: cab.id,
      provider: cab.provider,
      pickup,
      dropoff,
      pickupTime: nowTime,
      fare: cab.estimatedFare
    };

    const formattedTotal = Number(cab.estimatedFare).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    elements.checkoutOrderSummary.innerHTML = `
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Cab Provider:</span>
          <strong>${escapeHtml(cab.provider)} (${escapeHtml(cab.city)})</strong>
        </div>
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Route:</span>
          <span>${escapeHtml(pickup)} &rarr; ${escapeHtml(dropoff)}</span>
        </div>
        <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
          <span style="color: #94a3b8;">Est. Distance:</span>
          <span>${cab.estimatedDistanceKm} km</span>
        </div>
        <div style="display: flex; justify-content: space-between; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 0.5rem; margin-top: 0.5rem; font-size: 1.1rem;">
          <span style="color: #f8fafc; font-weight: 700;">Total Demo Fare:</span>
          <span style="color: #4ade80; font-weight: 800;">&#8377;${formattedTotal}</span>
        </div>
      `;

    populateCheckoutTripOptions();

    elements.btnPayNowText.textContent = `Pay \u20B9${formattedTotal}`;
    elements.cardHolderName.value = (state.currentUser && state.currentUser.name) ? state.currentUser.name : '';
    elements.cardNumber.value = '4111 2222 3333 4444';
    elements.cardExpiry.value = '12/28';
    elements.cardCvv.value = '123';
    elements.checkoutErrorMsg.classList.add('hidden');
    elements.checkoutErrorMsg.textContent = '';

    elements.modalCheckout.classList.remove('hidden');
    elements.modalCheckout.classList.add('is-open');
  }

  function populateCheckoutTripOptions() {
    const userTrips = (state.tripsList || []).filter(t => t.id !== 'TRIP-DEMO-001' && (state.currentUser && t.userId === state.currentUser.id));
    if (elements.checkoutTripSelect) {
      elements.checkoutTripSelect.innerHTML = '<option value="" disabled selected>Select an existing trip...</option>';
      userTrips.forEach(t => {
        const opt = document.createElement('option');
        opt.value = t.id;
        const legCount = t.bookings ? t.bookings.length : 0;
        opt.textContent = `${t.title} (${legCount} leg${legCount === 1 ? '' : 's'})`;
        elements.checkoutTripSelect.appendChild(opt);
      });
    }

    if (userTrips.length === 0) {
      if (elements.choiceCreateTrip) elements.choiceCreateTrip.checked = true;
      if (elements.choiceExistingTrip) {
        elements.choiceExistingTrip.disabled = true;
        if (elements.choiceExistingTrip.parentElement) {
          elements.choiceExistingTrip.parentElement.style.opacity = '0.5';
          elements.choiceExistingTrip.parentElement.title = 'No existing trips found for your account';
        }
      }
      if (elements.checkoutExistingTripContainer) elements.checkoutExistingTripContainer.classList.add('hidden');
    } else {
      if (elements.choiceExistingTrip) {
        elements.choiceExistingTrip.disabled = false;
        if (elements.choiceExistingTrip.parentElement) {
          elements.choiceExistingTrip.parentElement.style.opacity = '1';
          elements.choiceExistingTrip.parentElement.removeAttribute('title');
        }
      }
      if (elements.choiceCreateTrip && elements.choiceCreateTrip.checked && elements.checkoutExistingTripContainer) {
        elements.checkoutExistingTripContainer.classList.add('hidden');
      } else if (elements.choiceExistingTrip && elements.choiceExistingTrip.checked && elements.checkoutExistingTripContainer) {
        elements.checkoutExistingTripContainer.classList.remove('hidden');
      }
    }
  }

  function closeCheckoutModal() {
    if (elements.modalCheckout) {
      elements.modalCheckout.classList.add('hidden');
      elements.modalCheckout.classList.remove('is-open');
    }
  }

  const activeVerificationTimers = new Map();

  function startInCardVerificationTimer(card, statusBox, bookingId, timeoutSec, targetName, type) {
    if (!statusBox) return;
    const key = `${type}_${card.dataset.hotelId || card.dataset.cabId}`;
    if (activeVerificationTimers.has(key)) {
      clearInterval(activeVerificationTimers.get(key).timerId);
      clearInterval(activeVerificationTimers.get(key).pollId);
    }

    statusBox.classList.remove('hidden');
    statusBox.style.background = 'rgba(56, 189, 248, 0.1)';
    statusBox.style.borderColor = 'rgba(56, 189, 248, 0.3)';
    statusBox.style.color = '#38bdf8';

    let remaining = timeoutSec || 30;
    statusBox.innerHTML = `<span>✉️ Verification email sent! Waiting for ${escapeHtml(targetName || 'provider')} acceptance...</span><strong class="timer-countdown" style="font-family: monospace; font-size: 0.85rem; color: #f59e0b;">${remaining}s</strong>`;

    const timerId = setInterval(() => {
      remaining -= 1;
      const countdownEl = statusBox.querySelector('.timer-countdown');
      if (countdownEl) countdownEl.textContent = `${remaining}s`;
      if (remaining <= 0) {
        clearInterval(timerId);
        clearInterval(pollId);
        statusBox.style.background = 'rgba(239, 68, 68, 0.15)';
        statusBox.style.borderColor = 'rgba(239, 68, 68, 0.4)';
        statusBox.style.color = '#ef4444';
        statusBox.innerHTML = `<span>⚠️ <strong>Timeout (30s expired):</strong> Provider did not respond in time. Choose another option or retry.</span>`;
        const btn = card.querySelector('.btn-select-hotel, .btn-select-cab');
        if (btn) {
          btn.textContent = 'Select';
          btn.className = 'btn btn-sm btn-outline-gold ' + (type === 'HOTEL' ? 'btn-select-hotel' : 'btn-select-cab');
          btn.style.background = 'transparent';
          btn.style.color = 'var(--gold-color)';
        }
      }
    }, 1000);

    const pollId = setInterval(async () => {
      if (!bookingId) return;
      try {
        const res = await fetch(`/api/market/bookings/${encodeURIComponent(bookingId)}/status`);
        if (!res.ok) return;
        const json = await res.json();
        const b = json.data;
        if (!b) return;

        if (b.status === 'CONFIRMED' || b.providerConfirmationStatus === 'ACCEPTED') {
          clearInterval(timerId);
          clearInterval(pollId);
          statusBox.style.background = 'rgba(74, 222, 128, 0.15)';
          statusBox.style.borderColor = 'rgba(74, 222, 128, 0.4)';
          statusBox.style.color = '#4ade80';
          statusBox.innerHTML = `<span>✅ <strong>Provider Accepted!</strong> Booking verified and confirmed.</span>`;
          showToast(`Provider confirmed your ${type.toLowerCase()} booking!`, 'success');
        } else if (b.status === 'CANCELLED' || b.providerConfirmationStatus === 'REJECTED' || b.providerConfirmationStatus === 'EXPIRED') {
          clearInterval(timerId);
          clearInterval(pollId);
          statusBox.style.background = 'rgba(239, 68, 68, 0.15)';
          statusBox.style.borderColor = 'rgba(239, 68, 68, 0.4)';
          statusBox.style.color = '#ef4444';
          statusBox.innerHTML = `<span>❌ <strong>Provider Declined:</strong> ${escapeHtml(b.cannotHonourReason || 'Request not accepted')}. Please select another option.</span>`;
          const btn = card.querySelector('.btn-select-hotel, .btn-select-cab');
          if (btn) {
            btn.textContent = 'Select';
            btn.className = 'btn btn-sm btn-outline-gold ' + (type === 'HOTEL' ? 'btn-select-hotel' : 'btn-select-cab');
            btn.style.background = 'transparent';
            btn.style.color = 'var(--gold-color)';
          }
        }
      } catch (e) {
        // ignore transient poll error
      }
    }, 2000);

    activeVerificationTimers.set(key, { timerId, pollId });
  }

  async function loadCheckoutCabOptions(city, pickup, dropoff) {
    if (!elements.checkoutCabOptionsList) return;
    elements.checkoutCabOptionsList.innerHTML = `<div style="font-size: 0.85rem; color: #94a3b8; text-align: center; padding: 0.5rem;">Loading available cabs in ${escapeHtml(city || 'destination')}...</div>`;
    try {
      const query = new URLSearchParams({
        city: city || 'Bengaluru',
        pickup: pickup || 'Terminal',
        dropoff: dropoff || 'City Center'
      });
      const res = await fetch(`/api/market/cabs/quote?${query.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const quotes = (data && data.success && Array.isArray(data.data)) ? data.data : [];
      if (quotes.length === 0) {
        elements.checkoutCabOptionsList.innerHTML = `<div style="font-size: 0.85rem; color: #94a3b8; text-align: center; padding: 0.5rem;">No cabs available for this route.</div>`;
        if (elements.checkoutSelectedCabId) elements.checkoutSelectedCabId.value = '';
        return;
      }

      elements.checkoutCabOptionsList.innerHTML = quotes.map(cab => {
        const isAvailable = (cab.seats != null && cab.seats > 0);
        const fare = Number(cab.estimatedFare || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
        const isSelected = (elements.checkoutSelectedCabId && elements.checkoutSelectedCabId.value === cab.id);

        return `
            <div class="addon-option-card ${isAvailable ? '' : 'disabled'}" data-cab-id="${escapeHtml(cab.id)}" data-cab-provider="${escapeHtml(cab.provider)}" style="padding: 0.6rem; border: 1px solid ${isSelected ? '#38bdf8' : 'rgba(255,255,255,0.1)'}; border-radius: 6px; background: rgba(30, 41, 59, 0.4); opacity: ${isAvailable ? '1' : '0.5'}; margin-bottom: 0.4rem;">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <div style="flex: 1;">
                  <div style="font-weight: 600; font-size: 0.9rem; color: #f8fafc;">${escapeHtml(cab.provider)} <small style="color: #94a3b8;">(${escapeHtml(cab.vehicleCategory || 'SEDAN')})</small></div>
                  <div style="font-size: 0.75rem; color: #94a3b8; margin-top: 2px;">${escapeHtml(cab.listingLabel || '')} &bull; ${cab.seats || 4} seats</div>
                </div>
                <div style="display: flex; align-items: center; gap: 0.6rem;">
                  <div style="text-align: right;">
                    <div style="font-weight: 700; color: #4ade80;">₹${fare}</div>
                    ${isAvailable ? '<span class="badge badge-success" style="font-size: 0.7rem;">Available</span>' : '<span class="badge badge-neutral" style="font-size: 0.7rem;">Unavailable</span>'}
                  </div>
                  <button type="button" class="btn btn-sm ${isSelected ? 'btn-gold' : 'btn-outline-gold'} btn-select-cab" data-cab-id="${escapeHtml(cab.id)}" ${isAvailable ? '' : 'disabled'} style="min-width: 80px; padding: 0.3rem 0.5rem; font-size: 0.8rem; font-weight: 600;">
                    ${isSelected ? 'Selected &check;' : 'Select'}
                  </button>
                </div>
              </div>
              <div class="cab-verification-status hidden" style="margin-top: 0.4rem; padding: 0.35rem 0.5rem; background: rgba(56, 189, 248, 0.1); border: 1px solid rgba(56, 189, 248, 0.3); border-radius: 4px; font-size: 0.78rem; color: #38bdf8; display: flex; justify-content: space-between; align-items: center;"></div>
            </div>
          `;
      }).join('');

      const cards = elements.checkoutCabOptionsList.querySelectorAll('.addon-option-card:not(.disabled)');
      cards.forEach((card) => {
        const btn = card.querySelector('.btn-select-cab');
        const doSelect = async () => {
          cards.forEach(c => {
            c.style.borderColor = 'rgba(255,255,255,0.1)';
            const b = c.querySelector('.btn-select-cab');
            if (b) {
              b.textContent = 'Select';
              b.className = 'btn btn-sm btn-outline-gold btn-select-cab';
              b.style.background = 'transparent';
              b.style.color = 'var(--gold-color)';
            }
          });
          card.style.borderColor = '#38bdf8';
          if (btn) {
            btn.innerHTML = 'Selected &check;';
            btn.className = 'btn btn-sm btn-gold btn-select-cab';
            btn.style.background = 'var(--gold-color)';
            btn.style.color = '#0f172a';
            btn.style.fontWeight = '700';
          }
          if (elements.checkoutSelectedCabId) elements.checkoutSelectedCabId.value = card.dataset.cabId;

          // User requirement: verification email send to provider within 30 sec upon select
          const statusBox = card.querySelector('.cab-verification-status');
          if (statusBox) {
            statusBox.classList.remove('hidden');
            statusBox.innerHTML = `<span>⏳ Dispatching verification email to cab provider...</span>`;
          }
          try {
            const vRes = await fetch('/api/market/verification/request', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                type: 'CAB',
                targetId: card.dataset.cabId,
                targetName: card.dataset.cabProvider,
                city: city || (marketState.currentService && marketState.currentService.destinationCity),
                pickup: pickup || (elements.checkoutCabPickup ? elements.checkoutCabPickup.value : ''),
                dropoff: dropoff || (elements.checkoutCabDropoff ? elements.checkoutCabDropoff.value : ''),
                guestName: (state.currentUser && state.currentUser.name) ? state.currentUser.name : 'Traveller'
              })
            });
            const vData = await vRes.json();
            if (vRes.ok && vData.success && vData.data) {
              showToast(`Verification email sent to ${card.dataset.cabProvider}!`, 'info');
              startInCardVerificationTimer(card, statusBox, vData.data.bookingId, vData.data.timeoutSeconds || 30, card.dataset.cabProvider, 'CAB');
            }
          } catch (err) {
            console.error('Failed to request cab verification:', err);
          }
        };

        if (btn) btn.addEventListener('click', (ev) => { ev.stopPropagation(); doSelect(); });
        card.addEventListener('click', (ev) => {
          if (ev.target.closest('.btn-select-cab')) return;
          doSelect();
        });
      });
    } catch (err) {
      elements.checkoutCabOptionsList.innerHTML = `<div style="font-size: 0.85rem; color: #ef4444; text-align: center; padding: 0.5rem;">Failed to load cabs: ${escapeHtml(err.message)}</div>`;
    }
  }

  async function loadCheckoutHotelOptions(city, checkIn, nights, rooms) {
    if (!elements.checkoutHotelOptionsList) return;
    elements.checkoutHotelOptionsList.innerHTML = `<div style="font-size: 0.85rem; color: #94a3b8; text-align: center; padding: 0.5rem;">Loading available hotels in ${escapeHtml(city || 'destination')}...</div>`;
    try {
      const query = new URLSearchParams({
        city: city || 'Bengaluru',
        rooms: rooms || 1
      });
      if (checkIn) query.append('checkIn', checkIn);
      const res = await fetch(`/api/market/hotels?${query.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const hotels = (data && data.success && Array.isArray(data.data)) ? data.data : [];
      if (hotels.length === 0) {
        elements.checkoutHotelOptionsList.innerHTML = `<div style="font-size: 0.85rem; color: #94a3b8; text-align: center; padding: 0.5rem;">No hotels found for this city.</div>`;
        if (elements.checkoutSelectedHotelId) elements.checkoutSelectedHotelId.value = '';
        if (elements.checkoutSelectedRoomTypeId) elements.checkoutSelectedRoomTypeId.value = '';
        return;
      }

      // Sort: available first, then cheap to high by price
      hotels.sort((a, b) => {
        const aAvail = (a.roomsAvailable != null && a.roomsAvailable >= (rooms || 1)) ? 0 : 1;
        const bAvail = (b.roomsAvailable != null && b.roomsAvailable >= (rooms || 1)) ? 0 : 1;
        if (aAvail !== bAvail) return aAvail - bAvail;
        const aPrice = Number(a.pricePerNight || a.estimatedTotal || 0);
        const bPrice = Number(b.pricePerNight || b.estimatedTotal || 0);
        return aPrice - bPrice;
      });

      elements.checkoutHotelOptionsList.innerHTML = hotels.map(h => {
        const isAvailable = (h.roomsAvailable != null && h.roomsAvailable >= (rooms || 1));
        const total = Number(h.estimatedTotal || (h.pricePerNight * (nights || 1) * (rooms || 1))).toLocaleString('en-IN', { minimumFractionDigits: 2 });
        const ratingHtml = (h.rating != null && Number(h.rating) > 0)
          ? `<span style="color: #f59e0b; font-weight: 700; font-size: 0.82rem; margin-left: 0.35rem;" title="${Number(h.rating).toFixed(1)} Stars">★ ${Number(h.rating).toFixed(1)}</span>`
          : '<span style="color: #94a3b8; font-size: 0.72rem; margin-left: 0.35rem;">(Provider Listed)</span>';

        const isSelected = (elements.checkoutSelectedHotelId && elements.checkoutSelectedHotelId.value === h.id);

        return `
            <div class="addon-option-card ${isAvailable ? '' : 'disabled'}" data-hotel-id="${escapeHtml(h.id)}" data-room-type-id="${escapeHtml(h.roomTypeId || '')}" data-hotel-name="${escapeHtml(h.name)}" style="padding: 0.6rem; border: 1px solid ${isSelected ? '#38bdf8' : 'rgba(255,255,255,0.1)'}; border-radius: 6px; background: rgba(30, 41, 59, 0.4); opacity: ${isAvailable ? '1' : '0.5'}; margin-bottom: 0.4rem;">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <div style="flex: 1;">
                  <div style="display: flex; align-items: center; gap: 0.3rem;">
                    <span style="font-weight: 600; font-size: 0.9rem; color: #f8fafc;">${escapeHtml(h.name)}</span>
                    ${ratingHtml}
                  </div>
                  <div style="font-size: 0.75rem; color: #94a3b8; margin-top: 2px;">${escapeHtml(h.roomTypeName || 'Standard Room')} &bull; ${escapeHtml(h.listingLabel || '')}</div>
                </div>
                <div style="display: flex; align-items: center; gap: 0.6rem;">
                  <div style="text-align: right;">
                    <div style="font-weight: 700; color: #4ade80;">₹${total}</div>
                    ${isAvailable ? `<span class="badge badge-success" style="font-size: 0.7rem;">${h.roomsAvailable} left</span>` : '<span class="badge badge-danger" style="font-size: 0.7rem;">Sold out</span>'}
                  </div>
                  <button type="button" class="btn btn-sm ${isSelected ? 'btn-gold' : 'btn-outline-gold'} btn-select-hotel" data-hotel-id="${escapeHtml(h.id)}" ${isAvailable ? '' : 'disabled'} style="min-width: 80px; padding: 0.3rem 0.5rem; font-size: 0.8rem; font-weight: 600;">
                    ${isSelected ? 'Selected &check;' : 'Select'}
                  </button>
                </div>
              </div>
              <div class="hotel-verification-status hidden" style="margin-top: 0.4rem; padding: 0.35rem 0.5rem; background: rgba(56, 189, 248, 0.1); border: 1px solid rgba(56, 189, 248, 0.3); border-radius: 4px; font-size: 0.78rem; color: #38bdf8; display: flex; justify-content: space-between; align-items: center;"></div>
            </div>
          `;
      }).join('');

      const cards = elements.checkoutHotelOptionsList.querySelectorAll('.addon-option-card:not(.disabled)');
      cards.forEach((card) => {
        const btn = card.querySelector('.btn-select-hotel');
        const doSelect = async () => {
          cards.forEach(c => {
            c.style.borderColor = 'rgba(255,255,255,0.1)';
            const b = c.querySelector('.btn-select-hotel');
            if (b) {
              b.textContent = 'Select';
              b.className = 'btn btn-sm btn-outline-gold btn-select-hotel';
              b.style.background = 'transparent';
              b.style.color = 'var(--gold-color)';
            }
          });
          card.style.borderColor = '#38bdf8';
          if (btn) {
            btn.innerHTML = 'Selected &check;';
            btn.className = 'btn btn-sm btn-gold btn-select-hotel';
            btn.style.background = 'var(--gold-color)';
            btn.style.color = '#0f172a';
            btn.style.fontWeight = '700';
          }
          if (elements.checkoutSelectedHotelId) elements.checkoutSelectedHotelId.value = card.dataset.hotelId;
          if (elements.checkoutSelectedRoomTypeId) elements.checkoutSelectedRoomTypeId.value = card.dataset.roomTypeId;
          if (elements.checkoutHotelName) elements.checkoutHotelName.value = card.dataset.hotelName || '';

          // User requirement: Destination cab dropoff should be hotel name
          if (elements.checkoutCabDropoff) {
            elements.checkoutCabDropoff.value = card.dataset.hotelName || '';
          }

          // If cab transfer is enabled, refresh cab quotes for the selected hotel destination
          if (elements.checkoutAddonCabToggle && elements.checkoutAddonCabToggle.checked && marketState.currentService) {
            const destCity = marketState.currentService.destinationCity || '';
            const pickup = elements.checkoutCabPickup ? elements.checkoutCabPickup.value : '';
            loadCheckoutCabOptions(destCity, pickup, card.dataset.hotelName);
          }

          // User requirement: verification email send to provider within 30 sec upon select
          const statusBox = card.querySelector('.hotel-verification-status');
          if (statusBox) {
            statusBox.classList.remove('hidden');
            statusBox.innerHTML = `<span>⏳ Dispatching verification email to provider...</span>`;
          }
          try {
            const vRes = await fetch('/api/market/verification/request', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                type: 'HOTEL',
                targetId: card.dataset.hotelId,
                roomTypeId: card.dataset.roomTypeId,
                targetName: card.dataset.hotelName,
                city: city || (marketState.currentService && marketState.currentService.destinationCity),
                rooms: rooms || 1,
                nights: nights || 1,
                guestName: (state.currentUser && state.currentUser.name) ? state.currentUser.name : 'Traveller'
              })
            });
            const vData = await vRes.json();
            if (vRes.ok && vData.success && vData.data) {
              showToast(`Verification email sent to provider for ${card.dataset.hotelName}!`, 'info');
              startInCardVerificationTimer(card, statusBox, vData.data.bookingId, vData.data.timeoutSeconds || 30, card.dataset.hotelName, 'HOTEL');
            }
          } catch (err) {
            console.error('Failed to request verification:', err);
          }
        };

        if (btn) btn.addEventListener('click', (ev) => { ev.stopPropagation(); doSelect(); });
        card.addEventListener('click', (ev) => {
          if (ev.target.closest('.btn-select-hotel')) return;
          doSelect();
        });
      });
    } catch (err) {
      elements.checkoutHotelOptionsList.innerHTML = `<div style="font-size: 0.85rem; color: #ef4444; text-align: center; padding: 0.5rem;">Failed to load hotels: ${escapeHtml(err.message)}</div>`;
    }
  }

  let pendingConfirmationTimerId = null;
  let pendingConfirmationPollId = null;

  function openPendingConfirmationModal(booking) {
    if (!elements.modalProviderPending) return;
    clearInterval(pendingConfirmationTimerId);
    clearInterval(pendingConfirmationPollId);

    elements.modalProviderPending.classList.remove('hidden');
    elements.modalProviderPending.classList.add('is-open');

    if (elements.pendingActionFeedback) {
      elements.pendingActionFeedback.className = 'hidden';
      elements.pendingActionFeedback.textContent = '';
    }
    if (elements.pendingProviderMessage) {
      elements.pendingProviderMessage.textContent = 'Waiting for provider to confirm your request...';
    }
    if (elements.btnCancelPendingRequest) {
      elements.btnCancelPendingRequest.textContent = 'Choose Another Option';
    }

    let totalSeconds = 60;
    if (booking.confirmationExpiresAt) {
      const diff = Math.round((new Date(booking.confirmationExpiresAt).getTime() - Date.now()) / 1000);
      if (diff > 0) totalSeconds = diff;
    }
    let remainingSeconds = totalSeconds;

    const updateTimerDisplay = () => {
      if (elements.pendingCountdownTimer) {
        elements.pendingCountdownTimer.textContent = `${remainingSeconds}s`;
      }
      if (elements.pendingProgressBar) {
        const pct = Math.max(0, Math.min(100, (remainingSeconds / totalSeconds) * 100));
        elements.pendingProgressBar.style.width = `${pct}%`;
      }
    };

    updateTimerDisplay();

    pendingConfirmationTimerId = setInterval(() => {
      remainingSeconds--;
      if (remainingSeconds <= 0) {
        clearInterval(pendingConfirmationTimerId);
        clearInterval(pendingConfirmationPollId);
        if (elements.pendingCountdownTimer) elements.pendingCountdownTimer.textContent = '0s';
        if (elements.pendingProgressBar) elements.pendingProgressBar.style.width = '0%';
        if (elements.pendingProviderMessage) {
          elements.pendingProviderMessage.textContent = 'Request timed out. The provider did not respond in time.';
        }
        if (elements.pendingActionFeedback) {
          elements.pendingActionFeedback.className = 'badge-danger';
          elements.pendingActionFeedback.style.display = 'block';
          elements.pendingActionFeedback.textContent = 'Not available — please choose another option.';
        }
      } else {
        updateTimerDisplay();
      }
    }, 1000);

    pendingConfirmationPollId = setInterval(async () => {
      try {
        const res = await fetch(`/api/market/bookings/${encodeURIComponent(booking.id)}/status`);
        if (!res.ok) return;
        const data = await res.json();
        if (!data.success || !data.data) return;

        const current = data.data;
        if (current.status === 'CONFIRMED') {
          clearInterval(pendingConfirmationTimerId);
          clearInterval(pendingConfirmationPollId);
          if (elements.pendingProviderMessage) {
            elements.pendingProviderMessage.textContent = 'Provider Confirmed! Payment processed.';
          }
          if (elements.pendingActionFeedback) {
            elements.pendingActionFeedback.className = 'badge-success';
            elements.pendingActionFeedback.style.display = 'block';
            elements.pendingActionFeedback.textContent = 'Booking confirmed! Redirecting to confirmation...';
          }
          setTimeout(() => {
            closePendingConfirmationModal();
            openConfirmationModal(current);
            showToast('Provider confirmed! Booking complete.', 'success');
          }, 1200);
        } else if (current.status === 'CANCELLED' || current.providerConfirmationStatus === 'REJECTED' || current.providerConfirmationStatus === 'EXPIRED') {
          clearInterval(pendingConfirmationTimerId);
          clearInterval(pendingConfirmationPollId);
          if (elements.pendingProviderMessage) {
            elements.pendingProviderMessage.textContent = current.providerConfirmationStatus === 'REJECTED'
              ? 'Provider cannot honour this request at this time.'
              : 'Booking request timed out with no response from the provider.';
          }
          if (elements.pendingActionFeedback) {
            elements.pendingActionFeedback.className = 'badge-danger';
            elements.pendingActionFeedback.style.display = 'block';
            elements.pendingActionFeedback.textContent = 'Not available — no payment charged. Please choose another option.';
          }
        }
      } catch (e) {
        console.warn('Status poll failed', e);
      }
    }, 2000);
  }

  function closePendingConfirmationModal() {
    clearInterval(pendingConfirmationTimerId);
    clearInterval(pendingConfirmationPollId);
    if (elements.modalProviderPending) {
      elements.modalProviderPending.classList.add('hidden');
      elements.modalProviderPending.classList.remove('is-open');
    }
  }

  // Mock Payment Submit
  async function handleMockPaymentSubmit(e) {
    e.preventDefault();

    const selectedMethodEl = document.querySelector('input[name="paymentMethod"]:checked');
    const paymentMethod = selectedMethodEl ? selectedMethodEl.value : 'CARD';

    let cardHolder = '';
    let cardNumber = '';
    let expiry = '';
    let cvv = '';
    let upiId = '';
    let bankName = '';

    if (paymentMethod === 'CARD') {
      cardHolder = elements.cardHolderName ? elements.cardHolderName.value.trim() : '';
      cardNumber = elements.cardNumber ? elements.cardNumber.value.trim() : '';
      expiry = elements.cardExpiry ? elements.cardExpiry.value.trim() : '';
      cvv = elements.cardCvv ? elements.cardCvv.value.trim() : '';

      if (!cardHolder || !cardNumber || !expiry || !cvv) {
        showToast('Please fill in all card details.', 'warning');
        return;
      }
    } else if (paymentMethod === 'UPI') {
      upiId = elements.checkoutUpiId ? elements.checkoutUpiId.value.trim() : '';
      if (!upiId) {
        showToast('Please enter your UPI ID.', 'warning');
        return;
      }
      if (!upiId.includes('@')) {
        showToast('Invalid UPI ID format. Expected: username@bank', 'warning');
        return;
      }
    } else if (paymentMethod === 'NETBANKING') {
      bankName = elements.checkoutBankSelect ? elements.checkoutBankSelect.value : '';
      if (!bankName) {
        showToast('Please select your bank for Netbanking.', 'warning');
        return;
      }
    }

    let bookingDraft = {};
    const isExisting = elements.choiceExistingTrip && elements.choiceExistingTrip.checked;
    if (isExisting) {
      const selectedTripId = elements.checkoutTripSelect ? elements.checkoutTripSelect.value : '';
      if (!selectedTripId) {
        showToast('Please select an existing trip from the dropdown.', 'warning');
        return;
      }
      bookingDraft.tripId = selectedTripId;
      bookingDraft.createTrip = false;
    } else {
      bookingDraft.createTrip = true;
      bookingDraft.tripId = null;
    }

    if (marketState.checkoutType === 'HOTEL') {
      const hotel = marketState.pendingHotel;
      if (!hotel) return;
      bookingDraft.hotelId = hotel.hotelId;
      bookingDraft.roomTypeId = hotel.roomTypeId;
      bookingDraft.checkIn = hotel.checkIn;
      bookingDraft.checkOut = hotel.checkOut;
      bookingDraft.rooms = hotel.rooms;
      bookingDraft.guestName = hotel.guestName;
    } else if (marketState.checkoutType === 'CAB') {
      const cab = marketState.pendingCab;
      if (!cab) return;
      bookingDraft.cabServiceId = cab.cabServiceId;
      bookingDraft.pickup = cab.pickup;
      bookingDraft.dropoff = cab.dropoff;
      bookingDraft.pickupTime = cab.pickupTime;
    } else {
      const service = marketState.currentService;
      const passengers = marketState.passengers;
      if (!service || !passengers || passengers.length === 0) return;
      bookingDraft.serviceDepartureId = service.departureId;
      bookingDraft.passengers = passengers;

      // Check if Cab add-on is ticked
      if (elements.checkoutAddonCabToggle && elements.checkoutAddonCabToggle.checked) {
        const pickup = elements.checkoutCabPickup ? elements.checkoutCabPickup.value.trim() : (service.destinationStop || service.destinationCity);
        const dropoff = elements.checkoutCabDropoff ? elements.checkoutCabDropoff.value.trim() : `${service.destinationCity} Center`;
        const cabId = elements.checkoutSelectedCabId ? elements.checkoutSelectedCabId.value : null;
        bookingDraft.cabAddon = {
          cabServiceId: cabId || null,
          pickup: pickup,
          dropoff: dropoff,
          city: service.destinationCity
        };
      }

      // Check if Hotel add-on is ticked
      if (elements.checkoutAddonHotelToggle && elements.checkoutAddonHotelToggle.checked) {
        const hotelName = elements.checkoutHotelName ? elements.checkoutHotelName.value.trim() : `${service.destinationCity} Grand Residency`;
        const nights = elements.checkoutHotelNights ? parseInt(elements.checkoutHotelNights.value, 10) || 1 : 1;
        const rooms = elements.checkoutHotelRooms ? parseInt(elements.checkoutHotelRooms.value, 10) || 1 : 1;
        const hotelId = elements.checkoutSelectedHotelId ? elements.checkoutSelectedHotelId.value : null;
        const roomTypeId = elements.checkoutSelectedRoomTypeId ? elements.checkoutSelectedRoomTypeId.value : null;
        bookingDraft.hotelAddon = {
          hotelId: hotelId || null,
          roomTypeId: roomTypeId || null,
          hotelName: hotelName,
          city: service.destinationCity,
          rooms: rooms,
          nights: nights
        };
      }
    }

    setButtonLoading(elements.btnPayNow, true);
    elements.checkoutErrorMsg.classList.add('hidden');
    elements.checkoutErrorMsg.textContent = '';

    try {
      const payload = {
        bookingDraft,
        paymentMethod,
        cardHolder: cardHolder || (state.currentUser ? state.currentUser.name : 'Traveller'),
        cardNumber: paymentMethod === 'CARD' ? cardNumber : undefined,
        expiry: paymentMethod === 'CARD' ? expiry : undefined,
        cvv: paymentMethod === 'CARD' ? cvv : undefined,
        upiId: paymentMethod === 'UPI' ? upiId : undefined,
        bankName: paymentMethod === 'NETBANKING' ? bankName : undefined,
      };

      const res = await fetch('/api/market/payments/mock', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Payment processing failed');
      }

      const confirmed = data.data && data.data.confirmedBooking;
      const pendingAddon = data.data && (data.data.pendingAddonBooking || (confirmed && confirmed.pendingAddon));
      if (!confirmed) {
        throw new Error('Booking could not be confirmed');
      }

      closeCheckoutModal();
      if (confirmed.status === 'PENDING_PROVIDER_CONFIRMATION') {
        openPendingConfirmationModal(confirmed);
      } else if (pendingAddon && pendingAddon.status === 'PENDING_PROVIDER_CONFIRMATION') {
        openPendingConfirmationModal(pendingAddon);
      } else {
        openConfirmationModal(confirmed);
        showToast('Demo payment succeeded and booking confirmed!', 'success');
      }
      await fetchTripsDirectory();
      loadMyMarketBookings();
    } catch (err) {
      elements.checkoutErrorMsg.textContent = err.message;
      elements.checkoutErrorMsg.classList.remove('hidden');
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnPayNow, false);
    }
  }

  // Confirmation Modal
  function openConfirmationModal(booking) {
    if (!elements.modalConfirmation) return;

    elements.confirmedBookingRef.textContent = booking.bookingReference;
    elements.btnDownloadConfirmedSummary.href = `/api/market/bookings/${encodeURIComponent(booking.id)}/summary`;

    if (booking.tripId && elements.confirmedTripInfo) {
      elements.confirmedTripInfo.classList.remove('hidden');
      if (elements.confirmedTripTitle) {
        elements.confirmedTripTitle.textContent = booking.tripTitle || 'Trip';
      }
      if (elements.btnOpenConfirmedTrip) {
        elements.btnOpenConfirmedTrip.setAttribute('data-trip-id', booking.tripId);
      }
    } else if (elements.confirmedTripInfo) {
      elements.confirmedTripInfo.classList.add('hidden');
    }

    const priceFormatted = Number(booking.cost).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    elements.confirmedBookingDetails.innerHTML = `
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; font-size: 0.875rem;">
          <div><span style="color: #94a3b8;">Route:</span> <strong>${escapeHtml(booking.from)} &rarr; ${escapeHtml(booking.to)}</strong></div>
          <div><span style="color: #94a3b8;">Operator:</span> <strong>${escapeHtml(booking.operator || booking.provider)} (${escapeHtml(booking.vehicleNumber || 'N/A')})</strong></div>
          <div><span style="color: #94a3b8;">Seat(s):</span> <strong style="color: #4ade80;">${escapeHtml(booking.seatNumbers || 'Assigned')}</strong></div>
          <div><span style="color: #94a3b8;">Fare Paid:</span> <strong style="color: #fde047;">&#8377;${priceFormatted}</strong></div>
          <div><span style="color: #94a3b8;">Passenger(s):</span> <span>${escapeHtml(booking.passengerNames || 'Traveller')}</span></div>
          <div><span style="color: #94a3b8;">Status:</span> <span class="badge badge-green">${escapeHtml(booking.status)}</span></div>
        </div>
      `;

    elements.modalConfirmation.classList.remove('hidden');
    elements.modalConfirmation.classList.add('is-open');
  }

  function closeConfirmationModal() {
    if (elements.modalConfirmation) {
      elements.modalConfirmation.classList.add('hidden');
      elements.modalConfirmation.classList.remove('is-open');
    }
  }

  // My Marketplace Bookings API & Rendering
  async function loadMyMarketBookings() {
    if (!state.currentUser) return;

    try {
      const res = await fetch('/api/market/bookings');
      if (!res.ok) {
        if (elements.marketBookingsContainer) {
          elements.marketBookingsContainer.innerHTML = `<div class="market-empty-state"><p style="color: #f87171;">Unable to load marketplace bookings (HTTP ${res.status}).</p></div>`;
        }
        return;
      }

      const data = await res.json();
      const bookings = (data && data.success && data.data) ? data.data : [];

      if (elements.navMarketBookingsCount) elements.navMarketBookingsCount.textContent = String(bookings.length);
      if (elements.marketMyBookingsCount) elements.marketMyBookingsCount.textContent = `${bookings.length} booking${bookings.length === 1 ? '' : 's'}`;

      if (!elements.marketBookingsContainer) return;

      if (bookings.length === 0) {
        elements.marketBookingsContainer.innerHTML = `
            <div class="market-empty-state">
              <p>You have no marketplace bookings yet. Search for a route to create your first demo booking.</p>
            </div>
          `;
        return;
      }

      elements.marketBookingsContainer.innerHTML = '';
      bookings.forEach((b) => {
        const item = document.createElement('div');
        item.className = 'market-booking-item';

        const isCancelled = b.status === 'CANCELLED';
        const priceFormatted = Number(b.cost).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        const depTime = b.startTime ? String(b.startTime).replace('T', ' ') : 'N/A';

        item.innerHTML = `
            <div class="booking-item-header">
              <div>
                <span class="booking-item-ref">${escapeHtml(b.bookingReference || b.id)}</span>
                <span class="badge ${getStatusBadgeClass(b.type)}" style="margin-left: 0.5rem;">${escapeHtml(b.type)}</span>
              </div>
              <div>
                <span class="badge ${isCancelled ? 'badge-red' : 'badge-green'}">${escapeHtml(b.status)}</span>
                <span class="badge ${b.paymentStatus === 'DEMO_REFUNDED' ? 'badge-purple' : 'badge-blue'}">${escapeHtml(b.paymentStatus || 'DEMO_PAID')}</span>
              </div>
            </div>

            <div class="booking-item-body">
              <div><span style="color: #94a3b8;">Route:</span><br><strong>${escapeHtml(b.from)} &rarr; ${escapeHtml(b.to)}</strong></div>
              <div><span style="color: #94a3b8;">Carrier:</span><br><strong>${escapeHtml(b.operator || b.provider)} (${escapeHtml(b.vehicleNumber || 'N/A')})</strong></div>
              <div><span style="color: #94a3b8;">Seats & Passengers:</span><br>${escapeHtml(b.passengerNames || 'Traveller')} [${escapeHtml(b.seatNumbers || 'Auto')}]</div>
              <div><span style="color: #94a3b8;">Departure Time:</span><br>${escapeHtml(depTime)}</div>
              <div><span style="color: #94a3b8;">Total Fare:</span><br><strong style="color: #4ade80;">&#8377;${priceFormatted}</strong></div>
              ${b.cabServiceContact ? `
              <div style="grid-column: 1 / -1; padding: 6px 10px; background: rgba(59, 130, 246, 0.1); border-radius: 4px; border: 1px solid rgba(59, 130, 246, 0.25); font-size: 0.85rem; color: #93c5fd;">
                🚕 <strong>${escapeHtml(b.cabServiceContact)}</strong>
                ${b.assignedDriverName ? `<br><span style="color: #cbd5e1; font-size: 0.8rem;">Assigned Driver: <strong>${escapeHtml(b.assignedDriverName)}</strong></span>` : ''}
              </div>
              ` : ''}
              ${b.tripId ? `
              <div style="grid-column: 1 / -1; padding-top: 6px; border-top: 1px solid rgba(255,255,255,0.08); display: flex; align-items: center; gap: 6px;">
                <span style="color: #94a3b8; font-size: 0.8rem;">Trip:</span>
                <a href="#" class="link-open-trip" data-trip-id="${escapeHtml(b.tripId)}" style="color: #58a6ff; text-decoration: underline; font-weight: 600; font-size: 0.85rem;">
                  ${escapeHtml(b.tripTitle || 'Trip Workspace')} &rarr;
                </a>
              </div>
              ` : ''}
            </div>

            <div class="booking-item-actions">
              <a href="/api/market/bookings/${encodeURIComponent(b.id)}/summary" target="_blank" rel="noopener noreferrer" class="btn btn-outline btn-sm">
                <span>📄 View Summary</span>
              </a>
              ${b.departureId && state.statusMode !== 'LIVE' ? `
                <button type="button" class="btn btn-outline btn-sm btn-simulate-departure" data-departure-id="${escapeHtml(b.departureId)}" data-operator="${escapeHtml(b.operator || b.provider)}" data-vehicle="${escapeHtml(b.vehicleNumber || '')}">
                  Simulated status
                </button>
              ` : ''}
              ${!isCancelled ? `
                <button type="button" class="btn btn-outline-danger btn-sm btn-trigger-cancel" data-id="${escapeHtml(b.id)}" data-ref="${escapeHtml(b.bookingReference)}" data-type="${escapeHtml(b.type)}">
                  Cancel Booking
                </button>
              ` : '<span style="font-size: 0.8rem; color: #a78bfa; align-self: center;">Refunded in demo</span>'}
            </div>
          `;

        elements.marketBookingsContainer.appendChild(item);
      });
    } catch (err) {
      console.error('Failed to load my bookings', err);
      if (elements.marketBookingsContainer) {
        elements.marketBookingsContainer.innerHTML = `<div class="market-empty-state"><p style="color: #f87171;">Unable to load marketplace bookings: ${escapeHtml(err.message)}</p></div>`;
      }
    }
  }

  // Cancel Booking Modal
  function openCancelBookingModal(id, ref) {
    marketState.bookingToCancelId = id;
    marketState.bookingToCancelRef = ref;
    if (elements.cancelModalRef) elements.cancelModalRef.textContent = ref;

    const b = (marketState.myBookings || []).find(x => x.id === id)
      || (state.trip && state.trip.bookings && state.trip.bookings.find(x => x.id === id));
    const bType = (b && b.type) ? String(b.type).toUpperCase() : '';

    if (elements.cancelModalPolicyNotice) {
      if (bType === 'HOTEL') {
        elements.cancelModalPolicyNotice.innerHTML = `
            <div class="banner banner-danger" style="font-size: 0.85rem; border: 1px solid rgba(239, 68, 68, 0.4); background: rgba(239, 68, 68, 0.12); color: #f87171; border-radius: 4px; padding: 8px 12px;">
              ⚠️ <strong>Hotel bookings are strictly non-refundable once confirmed. You will receive 0% refund.</strong>
            </div>
          `;
      } else if (bType === 'CAB' || bType === 'CAB_OFFERING') {
        elements.cancelModalPolicyNotice.innerHTML = `
            <div class="banner banner-info" style="font-size: 0.85rem; border: 1px solid rgba(59, 130, 246, 0.4); background: rgba(59, 130, 246, 0.12); color: #93c5fd; border-radius: 4px; padding: 8px 12px;">
              ℹ️ <strong>Cabs do not auto-refund upon cancellation. To request a refund, go to the Cost &amp; Refund Ledger and submit a refund request for provider review.</strong>
            </div>
          `;
      } else {
        elements.cancelModalPolicyNotice.innerHTML = `
            <div class="banner banner-info" style="font-size: 0.85rem; border: 1px solid rgba(59, 130, 246, 0.4); background: rgba(59, 130, 246, 0.12); color: #93c5fd; border-radius: 4px; padding: 8px 12px;">
              ℹ️ <strong>Tiered refund policy applies:</strong> 10+ days before departure (100%), 3–9 days (75%), 1–2 days (25%), &lt;24 hours (0%). Any non-refundable deduction will be recorded in the Cost &amp; Refund Ledger.
            </div>
          `;
      }
    }

    elements.modalCancelBooking.classList.remove('hidden');
    elements.modalCancelBooking.classList.add('is-open');
  }

  function closeCancelBookingModal() {
    if (elements.modalCancelBooking) {
      elements.modalCancelBooking.classList.add('hidden');
      elements.modalCancelBooking.classList.remove('is-open');
    }
    marketState.bookingToCancelId = null;
    marketState.bookingToCancelRef = null;
  }

  async function executeCancelBooking() {
    const id = marketState.bookingToCancelId;
    if (!id) return;

    setButtonLoading(elements.btnExecuteCancelBooking, true);
    try {
      const res = await fetch(`/api/market/bookings/${encodeURIComponent(id)}/cancel`, {
        method: 'POST',
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to cancel booking');
      }

        closeCancelBookingModal();
        const tierMsg = (data.data && data.data.refundTierApplied) ? data.data.refundTierApplied : 'Booking cancelled.';
        showToast('Booking cancelled! ' + tierMsg, 'success');
        if (state.currentTripId && state.trip && state.trip.bookings) {
          const tripB = state.trip.bookings.find(b => b.id === id || (b.bookingReference && b.bookingReference === marketState.bookingToCancelRef));
          if (tripB && tripB.status !== 'CANCELLED') {
            try {
              await fetch(`/api/trips/${encodeURIComponent(state.currentTripId)}/bookings/${encodeURIComponent(tripB.id)}/report-problem`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ type: 'CANCELLED', delayMinutes: 0, note: `Cancelled by traveller (${tripB.type})` })
              });
            } catch (_) {}
          }
        }
        await loadMyMarketBookings();
        if (state.currentTripId) {
          await loadTripById(state.currentTripId);
        }
      } catch (err) {
        showToast(err.message, 'danger');
      } finally {
        setButtonLoading(elements.btnExecuteCancelBooking, false);
      }
    }

  // Hotel & Cab Search Handlers
  async function handleMarketHotelsSearch(e) {
    if (e) e.preventDefault();
    const city = elements.marketHotelCity.value.trim();
    const checkIn = elements.marketHotelCheckin.value;
    const checkOut = elements.marketHotelCheckout.value;
    const rooms = elements.marketHotelRooms.value;

    setButtonLoading(elements.btnMarketHotelsSearch, true);
    try {
      const q = new URLSearchParams({ city, checkIn: checkIn || '', checkOut: checkOut || '', rooms: rooms || '1' });
      const res = await fetch(`/api/market/hotels?${q.toString()}`);
      const data = await res.json();
      const hotels = (data && data.success && data.data) ? data.data : [];
      marketState.hotels = hotels;

      if (!elements.marketHotelsList) return;
      if (hotels.length === 0) {
        elements.marketHotelsList.innerHTML = '<div class="market-empty-state"><p>No hotels found for this city.</p></div>';
        return;
      }

      elements.marketHotelsList.innerHTML = '';
      hotels.forEach(h => {
        const item = document.createElement('div');
        item.className = 'market-service-card';
        const isProvider = (h.source === 'PROVIDER');
        const ratingBadge = h.rating != null
          ? `<span class="badge badge-gold">★ ${h.rating}</span>`
          : `<span class="badge" style="background: rgba(255,255,255,0.1); color: #cbd5e1;">Not rated</span>`;
        const providerBadge = isProvider
          ? `<span class="badge badge-info" style="margin-left: 0.35rem;">${escapeHtml(h.listingLabel || 'Provider-listed')}</span>`
          : '';
        const roomInfo = h.roomTypeName
          ? `<div style="font-size: 0.82rem; color: var(--gold-color); margin-top: 0.2rem;">Room: <strong>${escapeHtml(h.roomTypeName)}</strong> (Max ${h.maxGuests || 2} guests) &bull; <span style="color: #38bdf8;">${h.roomsAvailable} room(s) left</span></div>`
          : '';
        const amenitiesInfo = h.amenities
          ? `<div style="font-size: 0.75rem; color: #94a3b8; margin-top: 0.15rem;">Amenities: ${escapeHtml(h.amenities)}</div>`
          : '';
        const disclaimerText = h.disclaimer
          ? `<div class="disclaimer-text" style="font-size: 0.72rem; color: #94a3b8; font-style: italic; margin-top: 0.35rem;">${escapeHtml(h.disclaimer)}</div>`
          : '';

        item.innerHTML = `
            <div class="service-card-top">
              <div>
                <strong style="font-size: 1.1rem; color: #fff;">${escapeHtml(h.name)}</strong>
                <div style="font-size: 0.8rem; color: #94a3b8;">${escapeHtml(h.address)}, ${escapeHtml(h.city)}</div>
                ${roomInfo}
                ${amenitiesInfo}
              </div>
              <div style="text-align: right;">
                ${ratingBadge}
                ${providerBadge}
              </div>
            </div>
            <div class="service-card-bottom">
              <div>
                <span style="color: #94a3b8; font-size: 0.85rem;">${h.nights} night(s), ${h.rooms} room(s): </span>
                <span class="service-price">&#8377;${Number(h.estimatedTotal).toLocaleString('en-IN')}</span>
                ${disclaimerText}
              </div>
              <button type="button" class="btn btn-outline btn-sm btn-book-hotel-now" data-id="${escapeHtml(h.id)}" data-room-type-id="${escapeHtml(h.roomTypeId || '')}" data-name="${escapeHtml(h.name)}">
                Reserve Hotel
              </button>
            </div>
          `;
        elements.marketHotelsList.appendChild(item);
      });
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnMarketHotelsSearch, false);
    }
  }

  async function handleMarketCabsQuote(e) {
    if (e) e.preventDefault();
    const city = elements.marketCabCity.value.trim();
    const pickup = elements.marketCabPickup.value.trim();
    const dropoff = elements.marketCabDropoff.value.trim();

    setButtonLoading(elements.btnMarketCabsQuote, true);
    try {
      const q = new URLSearchParams({ city, pickup, dropoff });
      const res = await fetch(`/api/market/cabs/quote?${q.toString()}`);
      const data = await res.json();
      const cabs = (data && data.success && data.data) ? data.data : [];
      marketState.cabs = cabs;

      if (!elements.marketCabsList) return;
      if (cabs.length === 0) {
        elements.marketCabsList.innerHTML = '<div class="market-empty-state"><p>No cab services found.</p></div>';
        return;
      }

      elements.marketCabsList.innerHTML = '';
      cabs.forEach(c => {
        const item = document.createElement('div');
        item.className = 'market-service-card';
        const isProvider = (c.source === 'PROVIDER');
        const sourceBadge = isProvider
          ? `<span class="badge badge-info">${escapeHtml(c.listingLabel || 'Provider-listed')}</span>`
          : `<span class="badge badge-green">${escapeHtml(c.listingLabel || 'Verified Partner')}</span>`;
        const categoryBadge = `<span class="badge badge-gold" style="margin-left: 0.35rem;">${escapeHtml(c.vehicleCategory || 'SEDAN')} (${c.seats || 4} seats)</span>`;
        const operatingHours = c.operatingHours
          ? `<div style="font-size: 0.75rem; color: #94a3b8; margin-top: 0.2rem;">Hours: ${escapeHtml(c.operatingHours)}</div>`
          : '';
        const disclaimerText = c.disclaimer
          ? `<div class="disclaimer-text" style="font-size: 0.72rem; color: #94a3b8; font-style: italic; margin-top: 0.35rem;">${escapeHtml(c.disclaimer)}</div>`
          : '';

        item.innerHTML = `
            <div class="service-card-top">
              <div>
                <strong style="font-size: 1.1rem; color: #fff;">${escapeHtml(c.provider)}</strong>
                <div style="font-size: 0.8rem; color: #94a3b8;">Est. Distance: ${c.estimatedDistanceKm} km</div>
                ${operatingHours}
              </div>
              <div style="text-align: right;">
                ${sourceBadge}
                ${categoryBadge}
              </div>
            </div>
            <div class="service-card-bottom">
              <div>
                <span style="color: #94a3b8; font-size: 0.85rem;">Estimated Fare (estimated): </span>
                <span class="service-price">&#8377;${Number(c.estimatedFare).toLocaleString('en-IN')}</span>
                ${disclaimerText}
              </div>
              <button type="button" class="btn btn-outline btn-sm btn-book-cab-now" data-id="${escapeHtml(c.id)}" data-name="${escapeHtml(c.provider)}">
                Request Cab
              </button>
            </div>
          `;
        elements.marketCabsList.appendChild(item);
      });
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnMarketCabsQuote, false);
    }
  }

  // Phase 10B: City Autocomplete & Popular Routes
  function setupPopularRoutes() {
    if (!elements.marketPopularRoutes) return;
    elements.marketPopularRoutes.querySelectorAll('.route-chip').forEach(btn => {
      btn.addEventListener('click', () => {
        const origin = btn.getAttribute('data-origin');
        const dest = btn.getAttribute('data-dest');
        if (elements.marketSearchOrigin) elements.marketSearchOrigin.value = origin;
        if (elements.marketSearchDest) elements.marketSearchDest.value = dest;
        handleMarketSearch();
      });
    });
  }

  function setupCityAutocomplete(inputEl, dropdownEl, fuzzyBoxEl) {
    if (!inputEl || !dropdownEl) return;
    let debounceTimer = null;

    inputEl.addEventListener('input', () => {
      clearTimeout(debounceTimer);
      const query = inputEl.value.trim();
      if (query.length < 1) {
        dropdownEl.classList.add('hidden');
        dropdownEl.innerHTML = '';
        if (fuzzyBoxEl) {
          fuzzyBoxEl.classList.add('hidden');
          fuzzyBoxEl.innerHTML = '';
        }
        return;
      }

      debounceTimer = setTimeout(async () => {
        try {
          const res = await fetch(`/api/market/cities?q=${encodeURIComponent(query)}`);
          if (!res.ok) return;
          const data = await res.json();
          const cities = data.data || [];

          if (data.message === 'City not found') {
            dropdownEl.classList.add('hidden');
            dropdownEl.innerHTML = '';
            if (fuzzyBoxEl && cities.length > 0) {
              fuzzyBoxEl.innerHTML = `<span>City not found. Did you mean: </span>` +
                cities.map(c => `<button type="button" class="fuzzy-chip" data-name="${escapeHtml(c.name)}">${escapeHtml(c.name)}</button>`).join('');
              fuzzyBoxEl.classList.remove('hidden');
              fuzzyBoxEl.querySelectorAll('.fuzzy-chip').forEach(btn => {
                btn.addEventListener('click', () => {
                  inputEl.value = btn.getAttribute('data-name');
                  fuzzyBoxEl.classList.add('hidden');
                });
              });
            }
            return;
          }

          if (fuzzyBoxEl) {
            fuzzyBoxEl.classList.add('hidden');
            fuzzyBoxEl.innerHTML = '';
          }

          if (cities.length === 0) {
            dropdownEl.classList.add('hidden');
            return;
          }

          dropdownEl.innerHTML = '';
          cities.forEach(c => {
            const item = document.createElement('div');
            item.className = 'autocomplete-item';
            const badges = [];
            if (c.hasAirport) badges.push('✈️');
            if (c.hasRailwayStation) badges.push('🚆');
            item.innerHTML = `
                <div>
                  <span class="city-name">${escapeHtml(c.name)}</span>
                  <span class="city-state"> (${escapeHtml(c.state)})</span>
                </div>
                <div style="font-size: 0.8rem;">${badges.join(' ')}</div>
              `;
            item.addEventListener('click', () => {
              inputEl.value = c.name;
              dropdownEl.classList.add('hidden');
            });
            dropdownEl.appendChild(item);
          });
          dropdownEl.classList.remove('hidden');
        } catch (err) {
          console.error('Autocomplete fetch error', err);
        }
      }, 200);
    });

    document.addEventListener('click', (e) => {
      if (!inputEl.contains(e.target) && !dropdownEl.contains(e.target)) {
        dropdownEl.classList.add('hidden');
      }
    });
  }

  // Phase 14: Trip Wallet Event Listeners
  if (elements.btnViewWallet) {
    elements.btnViewWallet.addEventListener('click', () => {
      if (elements.sectionWallet) {
        elements.sectionWallet.scrollIntoView({ behavior: 'smooth' });
      }
      if (state.currentTripId) {
        loadTripWallet(state.currentTripId);
      }
    });
  }

  if (elements.btnWalletPrint) {
    elements.btnWalletPrint.addEventListener('click', () => {
      if (!state.currentTripId) {
        showToast('No trip currently loaded', 'warning');
        return;
      }
      const includeEmerg = elements.walletIncludeEmergency && elements.walletIncludeEmergency.checked;
      const url = `/api/trips/${encodeURIComponent(state.currentTripId)}/wallet/print` + (includeEmerg ? '?includeEmergency=true' : '');
      window.open(url, '_blank');
    });
  }

  if (elements.btnWalletCalendar) {
    elements.btnWalletCalendar.addEventListener('click', () => {
      if (!state.currentTripId) {
        showToast('No trip currently loaded', 'warning');
        return;
      }
      window.location.href = `/api/trips/${encodeURIComponent(state.currentTripId)}/wallet/calendar`;
    });
  }

  if (elements.btnWalletPack) {
    elements.btnWalletPack.addEventListener('click', () => {
      if (!state.currentTripId) {
        showToast('No trip currently loaded', 'warning');
        return;
      }
      const includeEmerg = elements.walletIncludeEmergency && elements.walletIncludeEmergency.checked;
      const url = `/api/trips/${encodeURIComponent(state.currentTripId)}/wallet/pack` + (includeEmerg ? '?includeEmergency=true' : '');
      window.location.href = url;
    });
  }

  // Phase 15: Trip Readiness Event Listeners
  if (elements.btnViewReadiness) {
    elements.btnViewReadiness.addEventListener('click', () => {
      if (elements.sectionReadiness) {
        elements.sectionReadiness.scrollIntoView({ behavior: 'smooth' });
      }
      if (state.currentTripId) {
        loadTripReadiness(state.currentTripId);
      }
    });
  }

  if (elements.btnRefreshReadiness) {
    elements.btnRefreshReadiness.addEventListener('click', () => {
      if (state.currentTripId) {
        loadTripReadiness(state.currentTripId);
        showToast('Refreshing trip readiness...', 'info');
      }
    });
  }

  // Connect Marketplace Event Listeners
  function setupMarketplaceEventListeners() {
    setupMarketSubtabs();
    setupCityAutocomplete(elements.marketSearchOrigin, elements.originSuggestions, elements.originFuzzyBox);
    setupCityAutocomplete(elements.marketSearchDest, elements.destSuggestions, elements.destFuzzyBox);
    setupPopularRoutes();

    if (elements.formMarketSearch) elements.formMarketSearch.addEventListener('submit', handleMarketSearch);
    if (elements.formMarketHotels) elements.formMarketHotels.addEventListener('submit', handleMarketHotelsSearch);
    if (elements.formMarketCabs) elements.formMarketCabs.addEventListener('submit', handleMarketCabsQuote);

    // Dynamic automatic recalculation whenever pickup, dropoff, or city changes
    let cabQuoteDebounceTimer = null;
    function autoUpdateCabQuotes() {
      clearTimeout(cabQuoteDebounceTimer);
      cabQuoteDebounceTimer = setTimeout(() => {
        const city = elements.marketCabCity ? elements.marketCabCity.value.trim() : '';
        const pickup = elements.marketCabPickup ? elements.marketCabPickup.value.trim() : '';
        const dropoff = elements.marketCabDropoff ? elements.marketCabDropoff.value.trim() : '';
        if (city || pickup || dropoff) {
          handleMarketCabsQuote();
        }
      }, 350);
    }

    if (elements.marketCabCity) {
      elements.marketCabCity.addEventListener('input', autoUpdateCabQuotes);
      elements.marketCabCity.addEventListener('change', autoUpdateCabQuotes);
    }
    if (elements.marketCabPickup) {
      elements.marketCabPickup.addEventListener('input', autoUpdateCabQuotes);
      elements.marketCabPickup.addEventListener('change', autoUpdateCabQuotes);
    }
    if (elements.marketCabDropoff) {
      elements.marketCabDropoff.addEventListener('input', autoUpdateCabQuotes);
      elements.marketCabDropoff.addEventListener('change', autoUpdateCabQuotes);
    }

    if (elements.btnRefreshMarketBookings) elements.btnRefreshMarketBookings.addEventListener('click', loadMyMarketBookings);

    // Book service button click delegation
    if (elements.marketServicesList) {
      elements.marketServicesList.addEventListener('click', (e) => {
        const btn = e.target.closest('.btn-book-service');
        if (!btn) return;
        const depId = btn.getAttribute('data-departure-id');
        const service = marketState.services.find(s => s.departureId === depId);
        if (service) {
          openSeatSelectionModal(service);
        }
      });
    }

    // Hotel & Cab click delegation -> opens checkout modal with trip choice
    if (elements.marketHotelsList) {
      elements.marketHotelsList.addEventListener('click', (e) => {
        const btn = e.target.closest('.btn-book-hotel-now');
        if (!btn) return;
        const hotelId = btn.getAttribute('data-id');
        const hotel = (marketState.hotels || []).find(h => h.id === hotelId);
        if (hotel) {
          openHotelCheckoutModal(hotel);
        }
      });
    }

    if (elements.marketCabsList) {
      elements.marketCabsList.addEventListener('click', (e) => {
        const btn = e.target.closest('.btn-book-cab-now');
        if (!btn) return;
        const cabId = btn.getAttribute('data-id');
        const cab = (marketState.cabs || []).find(c => c.id === cabId);
        if (cab) {
          openCabCheckoutModal(cab);
        }
      });
    }

    // Checkout trip choice radio toggles
    if (elements.choiceCreateTrip) {
      elements.choiceCreateTrip.addEventListener('change', () => {
        if (elements.choiceCreateTrip.checked && elements.checkoutExistingTripContainer) {
          elements.checkoutExistingTripContainer.classList.add('hidden');
        }
      });
    }
    if (elements.choiceExistingTrip) {
      elements.choiceExistingTrip.addEventListener('change', () => {
        if (elements.choiceExistingTrip.checked && elements.checkoutExistingTripContainer) {
          elements.checkoutExistingTripContainer.classList.remove('hidden');
        }
      });
    }

    // Add-on checkboxes
    if (elements.checkoutAddonCabToggle) {
      elements.checkoutAddonCabToggle.addEventListener('change', () => {
        if (elements.checkoutAddonCabContainer) {
          const isChecked = elements.checkoutAddonCabToggle.checked;
          elements.checkoutAddonCabContainer.classList.toggle('hidden', !isChecked);
          if (isChecked) {
            const destCity = (marketState.currentService && marketState.currentService.destinationCity) || '';
            const pickup = elements.checkoutCabPickup ? elements.checkoutCabPickup.value : '';
            const dropoff = elements.checkoutCabDropoff ? elements.checkoutCabDropoff.value : '';
            loadCheckoutCabOptions(destCity, pickup, dropoff);
          }
        }
      });
    }
    if (elements.checkoutAddonHotelToggle) {
      elements.checkoutAddonHotelToggle.addEventListener('change', () => {
        if (elements.checkoutAddonHotelContainer) {
          const isChecked = elements.checkoutAddonHotelToggle.checked;
          elements.checkoutAddonHotelContainer.classList.toggle('hidden', !isChecked);
          const destCity = (marketState.currentService && marketState.currentService.destinationCity) || '';
          if (isChecked) {
            const nights = elements.checkoutHotelNights ? parseInt(elements.checkoutHotelNights.value, 10) || 1 : 1;
            const rooms = elements.checkoutHotelRooms ? parseInt(elements.checkoutHotelRooms.value, 10) || 1 : 1;
            loadCheckoutHotelOptions(destCity, null, nights, rooms);
          } else {
            if (elements.checkoutSelectedHotelId) elements.checkoutSelectedHotelId.value = '';
            if (elements.checkoutSelectedRoomTypeId) elements.checkoutSelectedRoomTypeId.value = '';
            if (elements.checkoutHotelName) elements.checkoutHotelName.value = '';
            if (elements.checkoutCabDropoff) {
              elements.checkoutCabDropoff.value = destCity ? `${destCity} Center` : '';
            }
            if (elements.checkoutAddonCabToggle && elements.checkoutAddonCabToggle.checked) {
              const pickup = elements.checkoutCabPickup ? elements.checkoutCabPickup.value : '';
              loadCheckoutCabOptions(destCity, pickup, elements.checkoutCabDropoff ? elements.checkoutCabDropoff.value : '');
            }
          }
        }
      });
    }

    // Allow traveller to edit pickup or dropoff location manually with dynamic recalculation
    let checkoutCabDebounceTimer = null;
    function debouncedLoadCheckoutCabOptions() {
      clearTimeout(checkoutCabDebounceTimer);
      checkoutCabDebounceTimer = setTimeout(() => {
        if (elements.checkoutAddonCabToggle && elements.checkoutAddonCabToggle.checked && marketState.currentService) {
          const destCity = marketState.currentService.destinationCity || '';
          const pickup = elements.checkoutCabPickup ? elements.checkoutCabPickup.value.trim() : '';
          const dropoff = elements.checkoutCabDropoff ? elements.checkoutCabDropoff.value.trim() : '';
          loadCheckoutCabOptions(destCity, pickup, dropoff);
        }
      }, 350);
    }

    if (elements.checkoutCabPickup) {
      elements.checkoutCabPickup.addEventListener('change', debouncedLoadCheckoutCabOptions);
      elements.checkoutCabPickup.addEventListener('input', debouncedLoadCheckoutCabOptions);
    }

    if (elements.checkoutCabDropoff) {
      elements.checkoutCabDropoff.addEventListener('change', debouncedLoadCheckoutCabOptions);
      elements.checkoutCabDropoff.addEventListener('input', debouncedLoadCheckoutCabOptions);
    }
    if (elements.checkoutHotelNights) {
      elements.checkoutHotelNights.addEventListener('change', () => {
        if (elements.checkoutAddonHotelToggle && elements.checkoutAddonHotelToggle.checked) {
          const destCity = (marketState.currentService && marketState.currentService.destinationCity) || '';
          const nights = parseInt(elements.checkoutHotelNights.value, 10) || 1;
          const rooms = parseInt(elements.checkoutHotelRooms.value, 10) || 1;
          loadCheckoutHotelOptions(destCity, null, nights, rooms);
        }
      });
    }
    if (elements.checkoutHotelRooms) {
      elements.checkoutHotelRooms.addEventListener('change', () => {
        if (elements.checkoutAddonHotelToggle && elements.checkoutAddonHotelToggle.checked) {
          const destCity = (marketState.currentService && marketState.currentService.destinationCity) || '';
          const nights = parseInt(elements.checkoutHotelNights.value, 10) || 1;
          const rooms = parseInt(elements.checkoutHotelRooms.value, 10) || 1;
          loadCheckoutHotelOptions(destCity, null, nights, rooms);
        }
      });
    }
    if (elements.btnCancelPendingRequest) {
      elements.btnCancelPendingRequest.addEventListener('click', () => {
        closePendingConfirmationModal();
        if (elements.modalCheckout) {
          elements.modalCheckout.classList.remove('hidden');
          elements.modalCheckout.classList.add('is-open');
        }
      });
    }

    // Payment method radios
    const handlePaymentMethodChange = (mode) => {
      if (elements.checkoutCardFields) elements.checkoutCardFields.classList.toggle('hidden', mode !== 'CARD');
      if (elements.checkoutUpiFields) elements.checkoutUpiFields.classList.toggle('hidden', mode !== 'UPI');
      if (elements.checkoutNetbankingFields) elements.checkoutNetbankingFields.classList.toggle('hidden', mode !== 'NETBANKING');
    };
    if (elements.paymentMethodCard) elements.paymentMethodCard.addEventListener('change', () => handlePaymentMethodChange('CARD'));
    if (elements.paymentMethodUpi) elements.paymentMethodUpi.addEventListener('change', () => handlePaymentMethodChange('UPI'));
    if (elements.paymentMethodNetbanking) elements.paymentMethodNetbanking.addEventListener('change', () => handlePaymentMethodChange('NETBANKING'));

    // Seat selection modal
    if (elements.btnCloseSeatModal) elements.btnCloseSeatModal.addEventListener('click', closeSeatSelectionModal);
    if (elements.btnCancelSeatSelection) elements.btnCancelSeatSelection.addEventListener('click', closeSeatSelectionModal);
    if (elements.btnProceedToCheckout) elements.btnProceedToCheckout.addEventListener('click', openCheckoutModal);

    if (elements.flightPassengerCount) {
      elements.flightPassengerCount.addEventListener('input', () => {
        const count = Math.min(6, Math.max(1, parseInt(elements.flightPassengerCount.value, 10) || 1));
        const list = [];
        for (let i = 1; i <= count; i++) list.push(`Seat ${i} (Auto)`);
        renderPassengerInputs(list);
      });
    }

    // Checkout & Mock Payment
    if (elements.btnCloseCheckoutModal) elements.btnCloseCheckoutModal.addEventListener('click', closeCheckoutModal);
    if (elements.btnCancelCheckout) {
      elements.btnCancelCheckout.addEventListener('click', () => {
        closeCheckoutModal();
        if (elements.modalSeatSelection && marketState.checkoutType === 'TRANSPORT') {
          elements.modalSeatSelection.classList.remove('hidden');
        }
      });
    }
    if (elements.formMockPayment) elements.formMockPayment.addEventListener('submit', handleMockPaymentSubmit);

    // Confirmation modal
    if (elements.btnCloseConfirmationModal) elements.btnCloseConfirmationModal.addEventListener('click', closeConfirmationModal);
    if (elements.btnConfirmGoToBookings) {
      elements.btnConfirmGoToBookings.addEventListener('click', () => {
        closeConfirmationModal();
        switchView('market-bookings');
      });
    }
    if (elements.btnOpenConfirmedTrip) {
      elements.btnOpenConfirmedTrip.addEventListener('click', () => {
        const tripId = elements.btnOpenConfirmedTrip.getAttribute('data-trip-id');
        closeConfirmationModal();
        if (tripId) {
          loadTripById(tripId);
        }
      });
    }

    // Bookings container click delegation (Cancel & Open Trip)
    if (elements.marketBookingsContainer) {
      elements.marketBookingsContainer.addEventListener('click', (e) => {
        const tripLink = e.target.closest('.link-open-trip');
        if (tripLink) {
          e.preventDefault();
          const tripId = tripLink.getAttribute('data-trip-id');
          if (tripId) {
            loadTripById(tripId);
          }
          return;
        }

        const btn = e.target.closest('.btn-trigger-cancel');
        if (!btn) return;
        const id = btn.getAttribute('data-id');
        const ref = btn.getAttribute('data-ref');
        openCancelBookingModal(id, ref);
      });
    }

    if (elements.btnCloseCancelModal) elements.btnCloseCancelModal.addEventListener('click', closeCancelBookingModal);
    if (elements.btnDismissCancelBooking) elements.btnDismissCancelBooking.addEventListener('click', closeCancelBookingModal);
    if (elements.btnExecuteCancelBooking) elements.btnExecuteCancelBooking.addEventListener('click', executeCancelBooking);

    // Phase 10C: Status Simulation & Replacement Modals
    if (elements.btnCloseSimulateModal) elements.btnCloseSimulateModal.addEventListener('click', closeSimulateDepartureModal);
    if (elements.btnDismissSimulate) elements.btnDismissSimulate.addEventListener('click', closeSimulateDepartureModal);
    if (elements.btnApplyDepSimulation) elements.btnApplyDepSimulation.addEventListener('click', handleApplyDepartureSimulation);
    if (elements.simulateStatusSelect) {
      elements.simulateStatusSelect.addEventListener('change', () => {
        if (elements.simulateDelayGroup) {
          elements.simulateDelayGroup.classList.toggle('hidden', elements.simulateStatusSelect.value !== 'DELAYED');
        }
      });
    }

    if (elements.btnCloseReplacementModal) elements.btnCloseReplacementModal.addEventListener('click', closeReplacementBookingModal);
    if (elements.btnDismissReplacement) elements.btnDismissReplacement.addEventListener('click', closeReplacementBookingModal);
    if (elements.btnConfirmBookReplacement) elements.btnConfirmBookReplacement.addEventListener('click', handleConfirmBookReplacement);

    // Global delegation for .btn-simulate-departure
    document.addEventListener('click', (e) => {
      const simBtn = e.target.closest('.btn-simulate-departure');
      if (simBtn) {
        e.preventDefault();
        const depId = simBtn.getAttribute('data-departure-id');
        const op = simBtn.getAttribute('data-operator');
        const veh = simBtn.getAttribute('data-vehicle');
        const status = simBtn.getAttribute('data-status') || 'SCHEDULED';
        const delay = parseInt(simBtn.getAttribute('data-delay'), 10) || 0;
        if (depId) {
          openSimulateDepartureModal(depId, op, veh, status, delay);
        }
      }
    });
  }

  // Modal Simulation Handlers
  let activeSimulationDepartureId = null;

  function openSimulateDepartureModal(departureId, operator, vehicleNumber, currentStatus, currentDelay) {
    activeSimulationDepartureId = departureId;
    if (elements.simulateDepartureInfo) {
      elements.simulateDepartureInfo.innerHTML = `
          <strong>Departure ID:</strong> <code class="font-mono">${escapeHtml(departureId)}</code><br>
          <strong>Service:</strong> ${escapeHtml(operator || 'Operator')} (${escapeHtml(vehicleNumber || 'Vehicle')})
        `;
    }
    if (elements.simulateStatusSelect) {
      elements.simulateStatusSelect.value = (currentStatus === 'DELAYED' || currentStatus === 'CANCELLED') ? currentStatus : 'SCHEDULED';
    }
    if (elements.simulateDelayMinutes) {
      elements.simulateDelayMinutes.value = currentDelay && currentDelay > 0 ? currentDelay : 75;
    }
    if (elements.simulateDelayGroup) {
      elements.simulateDelayGroup.classList.toggle('hidden', elements.simulateStatusSelect.value !== 'DELAYED');
    }
    if (elements.simulateErrorMsg) {
      elements.simulateErrorMsg.classList.add('hidden');
      elements.simulateErrorMsg.textContent = '';
    }
    if (elements.modalSimulateDeparture) {
      elements.modalSimulateDeparture.classList.remove('hidden');
      elements.modalSimulateDeparture.classList.add('is-open');
    }
  }

  function closeSimulateDepartureModal() {
    if (elements.modalSimulateDeparture) {
      elements.modalSimulateDeparture.classList.add('hidden');
      elements.modalSimulateDeparture.classList.remove('is-open');
    }
    activeSimulationDepartureId = null;
  }

  async function handleApplyDepartureSimulation() {
    if (!activeSimulationDepartureId) return;
    const newState = elements.simulateStatusSelect ? elements.simulateStatusSelect.value : 'SCHEDULED';
    const delayMinutes = newState === 'DELAYED' ? (parseInt(elements.simulateDelayMinutes.value, 10) || 60) : 0;

    setButtonLoading(elements.btnApplyDepSimulation, true);
    if (elements.simulateErrorMsg) {
      elements.simulateErrorMsg.classList.add('hidden');
      elements.simulateErrorMsg.textContent = '';
    }

    try {
      const res = await fetch('/api/market/simulate/departure', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          serviceDepartureId: activeSimulationDepartureId,
          state: newState,
          delayMinutes: delayMinutes
        })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Simulation failed');
      }

      closeSimulateDepartureModal();
      showToast(`Departure updated to ${newState}. Affected trips evaluated.`, 'success');

      if (state.currentTripId) {
        await loadTripById(state.currentTripId);
      }
      await fetchTripsDirectory();
      loadMyMarketBookings();
    } catch (err) {
      if (elements.simulateErrorMsg) {
        elements.simulateErrorMsg.textContent = err.message;
        elements.simulateErrorMsg.classList.remove('hidden');
      }
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnApplyDepSimulation, false);
    }
  }

  // Modal Replacement Booking Handlers
  let activeReplacementData = null;

  async function openReplacementBookingModal(tripId, oldBookingId, serviceDepartureId) {
    if (elements.replacementErrorMsg) {
      elements.replacementErrorMsg.classList.add('hidden');
      elements.replacementErrorMsg.textContent = '';
    }
    if (elements.replacementPreviewContent) {
      elements.replacementPreviewContent.innerHTML = '<p style="color: #94a3b8; font-style: italic;">Loading replacement preview and auto-assigned seats...</p>';
    }
    if (elements.modalReplacementBooking) {
      elements.modalReplacementBooking.classList.remove('hidden');
      elements.modalReplacementBooking.classList.add('is-open');
    }

    try {
      const res = await fetch(`/api/market/replacement/preview?tripId=${encodeURIComponent(tripId)}&oldBookingId=${encodeURIComponent(oldBookingId)}&serviceDepartureId=${encodeURIComponent(serviceDepartureId)}`);
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to load replacement preview');
      }

      const p = data.data;
      activeReplacementData = { tripId, oldBookingId, serviceDepartureId };

      const costFormatted = Number(p.replacementCost).toLocaleString('en-IN', { minimumFractionDigits: 2 });
      const refundFormatted = Number(p.refundAmount).toLocaleString('en-IN', { minimumFractionDigits: 2 });
      const netFormatted = Number(p.netCost).toLocaleString('en-IN', { minimumFractionDigits: 2 });

      elements.replacementPreviewContent.innerHTML = `
          <div style="background: rgba(255,255,255,0.03); border-radius: 8px; padding: 12px; margin-bottom: 12px; font-size: 0.88rem;">
            <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
              <span style="color: #94a3b8;">Service:</span>
              <strong>${escapeHtml(p.operator)} (${escapeHtml(p.vehicleNumber)})</strong>
            </div>
            <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
              <span style="color: #94a3b8;">Route:</span>
              <span>${escapeHtml(p.originCity)} &rarr; ${escapeHtml(p.destinationCity)}</span>
            </div>
            <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
              <span style="color: #94a3b8;">Departure Time:</span>
              <span>${escapeHtml(formatDateTime(p.departureTime))}</span>
            </div>
            <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
              <span style="color: #94a3b8;">Auto-assigned Seats:</span>
              <strong style="color: #4ade80;">${escapeHtml((p.assignedSeats || []).join(', '))} (${p.passengerCount} passenger${p.passengerCount === 1 ? '' : 's'})</strong>
            </div>
            <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
              <span style="color: #94a3b8;">Seats Left on Departure:</span>
              <span class="badge ${p.seatsLeft > 5 ? 'badge-neutral' : 'badge-amber'}">${p.seatsLeft} available</span>
            </div>
            <div style="border-top: 1px solid rgba(255,255,255,0.1); padding-top: 8px; margin-top: 8px;">
              <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
                <span style="color: #94a3b8;">Replacement Fare:</span>
                <span>&#8377;${costFormatted}</span>
              </div>
              <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
                <span style="color: #a78bfa;">Disrupted Booking Refund:</span>
                <span style="color: #a78bfa;">-&#8377;${refundFormatted}</span>
              </div>
              <div style="display: flex; justify-content: space-between; font-weight: 700; font-size: 1rem; padding-top: 4px; border-top: 1px dashed rgba(255,255,255,0.1);">
                <span style="color: #f8fafc;">Net Cost:</span>
                <span style="color: #fde047;">&#8377;${netFormatted}</span>
              </div>
            </div>
          </div>
        `;
    } catch (err) {
      if (elements.replacementPreviewContent) {
        elements.replacementPreviewContent.innerHTML = `<p class="form-error">${escapeHtml(err.message)}</p>`;
      }
    }
  }

  function closeReplacementBookingModal() {
    if (elements.modalReplacementBooking) {
      elements.modalReplacementBooking.classList.add('hidden');
      elements.modalReplacementBooking.classList.remove('is-open');
    }
    activeReplacementData = null;
  }

  async function handleConfirmBookReplacement() {
    if (!activeReplacementData) return;
    const { tripId, oldBookingId, serviceDepartureId } = activeReplacementData;

    setButtonLoading(elements.btnConfirmBookReplacement, true);
    if (elements.replacementErrorMsg) {
      elements.replacementErrorMsg.classList.add('hidden');
      elements.replacementErrorMsg.textContent = '';
    }

    try {
      const res = await fetch('/api/market/replacement/book', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tripId, oldBookingId, serviceDepartureId })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Replacement booking failed');
      }

      closeReplacementBookingModal();
      showToast('Replacement leg booked successfully! Seats assigned and demo refund issued.', 'success');

      await loadTripById(tripId);
      await fetchTripsDirectory();
      loadMyMarketBookings();
    } catch (err) {
      if (elements.replacementErrorMsg) {
        elements.replacementErrorMsg.textContent = err.message;
        elements.replacementErrorMsg.classList.remove('hidden');
      }
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnConfirmBookReplacement, false);
    }
  }

  // ==========================================
  // Phase 18A: Provider & Admin Portal Logic
  // ==========================================

  let activeAdminFilter = '';

  function setupProviderEventListeners() {
    // Nav buttons
    if (elements.navBtnProvider) {
      elements.navBtnProvider.addEventListener('click', () => switchView('provider'));
    }
    if (elements.navBtnAdmin) {
      elements.navBtnAdmin.addEventListener('click', () => switchView('admin'));
    }

    // Provider Dashboard Sub-Tabs
    const provTabs = [
      { btn: elements.btnProvTabVehicles, content: elements.provTabContentVehicles },
      { btn: elements.btnProvTabSchedules, content: elements.provTabContentSchedules },
      { btn: elements.btnProvTabDepartures, content: elements.provTabContentDepartures },
      { btn: elements.btnProvTabHotels, content: elements.provTabContentHotels },
      { btn: elements.btnProvTabCabs, content: elements.provTabContentCabs },
      { btn: elements.btnProvTabRefunds, content: elements.provTabContentRefunds },
      { btn: elements.btnProvTabPending, content: elements.provTabContentPending },
    ];

    provTabs.forEach(({ btn, content }) => {
      if (!btn) return;
      btn.addEventListener('click', () => {
        provTabs.forEach(t => {
          if (t.btn) t.btn.classList.remove('active');
          if (t.content) t.content.classList.add('hidden');
        });
        btn.classList.add('active');
        if (content) content.classList.remove('hidden');
        const subtabName = btn.id.replace('btn-prov-tab-', '');
        window.history.pushState({}, '', `/provider/${subtabName}`);
        if (subtabName === 'refunds') loadProviderRefunds();
        if (subtabName === 'pending') loadProviderPendingBookings();
      });
    });

    if (elements.btnRefreshProvPending) {
      elements.btnRefreshProvPending.addEventListener('click', loadProviderPendingBookings);
    }

    // Provider Onboarding Form
    if (elements.formProviderRegister) {
      elements.formProviderRegister.addEventListener('submit', handleProviderRegisterSubmit);
    }

    // Vehicle Actions
    if (elements.btnProvAddVehicle) {
      elements.btnProvAddVehicle.addEventListener('click', openAddVehicleModal);
    }
    if (elements.btnCloseProvVehicleModal) {
      elements.btnCloseProvVehicleModal.addEventListener('click', closeVehicleModal);
    }
    if (elements.btnCancelProvVehicle) {
      elements.btnCancelProvVehicle.addEventListener('click', closeVehicleModal);
    }
    if (elements.formProvVehicle) {
      elements.formProvVehicle.addEventListener('submit', handleSaveVehicleSubmit);
    }
    const provVehMode = document.getElementById('prov-veh-mode');
    if (provVehMode) {
      provVehMode.addEventListener('change', () => {
        const modeVal = provVehMode.value;
        const typeEl = document.getElementById('prov-veh-type');
        const seatsEl = document.getElementById('prov-veh-seats');
        if (modeVal === 'CAB') {
          if (typeEl) {
            typeEl.placeholder = 'e.g. Sedan (Dzire, Etios) or SUV (Innova)';
            if (!typeEl.value || typeEl.value.includes('Sleeper') || typeEl.value.includes('Coach') || typeEl.value.includes('Airbus')) {
              typeEl.value = 'Sedan (4 Seats)';
            }
          }
          if (seatsEl && (parseInt(seatsEl.value, 10) === 40 || parseInt(seatsEl.value, 10) === 72 || parseInt(seatsEl.value, 10) === 180)) {
            seatsEl.value = 4;
          }
        } else if (modeVal === 'BUS') {
          if (typeEl) {
            typeEl.placeholder = 'e.g. AC Multi-Axle Sleeper';
            if (!typeEl.value || typeEl.value.includes('Sedan') || typeEl.value.includes('SUV')) {
              typeEl.value = 'AC Multi-Axle Sleeper';
            }
          }
          if (seatsEl && parseInt(seatsEl.value, 10) === 4) {
            seatsEl.value = 40;
          }
        } else if (modeVal === 'TRAIN') {
          if (typeEl) {
            typeEl.placeholder = 'e.g. Express AC Chair Car';
            if (!typeEl.value || typeEl.value.includes('Sedan') || typeEl.value.includes('Sleeper')) {
              typeEl.value = 'Express Coach';
            }
          }
          if (seatsEl && (parseInt(seatsEl.value, 10) === 4 || parseInt(seatsEl.value, 10) === 40)) {
            seatsEl.value = 72;
          }
        } else if (modeVal === 'FLIGHT') {
          if (typeEl) {
            typeEl.placeholder = 'e.g. Airbus A320 / Boeing 737';
            if (!typeEl.value || typeEl.value.includes('Sedan') || typeEl.value.includes('Coach')) {
              typeEl.value = 'Airbus A320';
            }
          }
          if (seatsEl && (parseInt(seatsEl.value, 10) === 4 || parseInt(seatsEl.value, 10) === 40)) {
            seatsEl.value = 180;
          }
        }
      });
    }

    // Schedule Actions
    if (elements.btnProvAddSchedule) {
      elements.btnProvAddSchedule.addEventListener('click', openAddScheduleModal);
    }
    if (elements.btnCloseProvScheduleModal) {
      elements.btnCloseProvScheduleModal.addEventListener('click', closeScheduleModal);
    }
    if (elements.btnCancelProvSchedule) {
      elements.btnCancelProvSchedule.addEventListener('click', closeScheduleModal);
    }
    if (elements.formProvSchedule) {
      elements.formProvSchedule.addEventListener('submit', handleSaveScheduleSubmit);
    }

    // Departures Actions & Status Report
    if (elements.btnRefreshProvDepartures) {
      elements.btnRefreshProvDepartures.addEventListener('click', loadProviderDepartures);
    }
    if (elements.btnCloseProvStatusModal) {
      elements.btnCloseProvStatusModal.addEventListener('click', closeStatusModal);
    }
    if (elements.btnCancelProvStatus) {
      elements.btnCancelProvStatus.addEventListener('click', closeStatusModal);
    }
    if (elements.formProvStatus) {
      elements.formProvStatus.addEventListener('submit', handleSaveStatusSubmit);
    }
    if (elements.provStatusState) {
      elements.provStatusState.addEventListener('change', (e) => {
        if (elements.provStatusDelayGroup) {
          if (e.target.value === 'DELAYED') {
            elements.provStatusDelayGroup.classList.remove('hidden');
          } else {
            elements.provStatusDelayGroup.classList.add('hidden');
          }
        }
      });
    }

    // Passenger Manifest Modal
    if (elements.btnCloseProvBookingsModal) {
      elements.btnCloseProvBookingsModal.addEventListener('click', closeBookingsModal);
    }
    if (elements.btnCloseProvBookings) {
      elements.btnCloseProvBookings.addEventListener('click', closeBookingsModal);
    }

    // Offer Alternative Modal (Phase 19)
    if (elements.btnCloseProvOfferAltModal) {
      elements.btnCloseProvOfferAltModal.addEventListener('click', closeOfferAlternativeModal);
    }
    if (elements.btnCancelProvOfferAlt) {
      elements.btnCancelProvOfferAlt.addEventListener('click', closeOfferAlternativeModal);
    }
    if (elements.formProvOfferAlt) {
      elements.formProvOfferAlt.addEventListener('submit', handleOfferAlternativeSubmit);
    }
    if (elements.provOfferAltSelect) {
      elements.provOfferAltSelect.addEventListener('change', () => {
        if (elements.provOfferAltNewVehicleSection) {
          if (elements.provOfferAltSelect.value === 'NEW_VEHICLE') {
            elements.provOfferAltNewVehicleSection.classList.remove('hidden');
          } else {
            elements.provOfferAltNewVehicleSection.classList.add('hidden');
          }
        }
      });
    }

    // Phase 18B: Hotel Actions
    if (elements.btnProvAddHotel) {
      elements.btnProvAddHotel.addEventListener('click', openAddHotelModal);
    }
    if (elements.btnCloseProvHotelModal) {
      elements.btnCloseProvHotelModal.addEventListener('click', closeHotelModal);
    }
    if (elements.btnCancelProvHotel) {
      elements.btnCancelProvHotel.addEventListener('click', closeHotelModal);
    }
    if (elements.formProvHotel) {
      elements.formProvHotel.addEventListener('submit', handleSaveHotelSubmit);
    }

    // Room Type Actions
    if (elements.btnProvAddRoomType) {
      elements.btnProvAddRoomType.addEventListener('click', () => openAddRoomTypeModal(activeSelectedHotelId));
    }
    if (elements.btnCloseProvRoomModal) {
      elements.btnCloseProvRoomModal.addEventListener('click', closeRoomTypeModal);
    }
    if (elements.btnCancelProvRoom) {
      elements.btnCancelProvRoom.addEventListener('click', closeRoomTypeModal);
    }
    if (elements.formProvRoomType) {
      elements.formProvRoomType.addEventListener('submit', handleSaveRoomTypeSubmit);
    }
    if (elements.btnProvCloseRoomsView) {
      elements.btnProvCloseRoomsView.addEventListener('click', () => {
        if (elements.provHotelRoomsSection) elements.provHotelRoomsSection.classList.add('hidden');
        activeSelectedHotelId = null;
      });
    }

    // Availability Calendar Actions
    if (elements.btnCloseProvAvailModal) {
      elements.btnCloseProvAvailModal.addEventListener('click', closeRoomAvailabilityModal);
    }
    if (elements.btnCloseProvAvail) {
      elements.btnCloseProvAvail.addEventListener('click', closeRoomAvailabilityModal);
    }

    // Cab Actions
    if (elements.btnProvAddCab) {
      elements.btnProvAddCab.addEventListener('click', openAddCabModal);
    }
    if (elements.btnCloseProvCabModal) {
      elements.btnCloseProvCabModal.addEventListener('click', closeCabModal);
    }
    if (elements.btnCancelProvCab) {
      elements.btnCancelProvCab.addEventListener('click', closeCabModal);
    }
    if (elements.formProvCab) {
      elements.formProvCab.addEventListener('submit', handleSaveCabSubmit);
    }

    // Hotel Bookings Manifest & Cannot Honour Actions
    if (elements.btnCloseProvHotelBkgModal) {
      elements.btnCloseProvHotelBkgModal.addEventListener('click', closeHotelBookingsModal);
    }
    if (elements.btnCloseProvHotelBookings) {
      elements.btnCloseProvHotelBookings.addEventListener('click', closeHotelBookingsModal);
    }
    if (elements.btnCloseProvHonourModal) {
      elements.btnCloseProvHonourModal.addEventListener('click', closeCannotHonourModal);
    }
    if (elements.btnCancelProvHonour) {
      elements.btnCancelProvHonour.addEventListener('click', closeCannotHonourModal);
    }
    if (elements.formProvCannotHonour) {
      elements.formProvCannotHonour.addEventListener('submit', handleCannotHonourSubmit);
    }

    // Admin Review Queue Actions
    if (elements.btnRefreshAdminProviders) {
      elements.btnRefreshAdminProviders.addEventListener('click', () => loadAdminProviders(activeAdminFilter));
    }
    const adminFilterBtns = document.querySelectorAll('.admin-filter-btn');
    adminFilterBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        adminFilterBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        activeAdminFilter = btn.getAttribute('data-status') || '';
        loadAdminProviders(activeAdminFilter);
      });
    });
    if (elements.btnCloseAdminActionModal) {
      elements.btnCloseAdminActionModal.addEventListener('click', closeAdminActionModal);
    }
    if (elements.btnCancelAdminAction) {
      elements.btnCancelAdminAction.addEventListener('click', closeAdminActionModal);
    }
    if (elements.formAdminAction) {
      elements.formAdminAction.addEventListener('submit', handleAdminActionSubmit);
    }

    // Phase 18C: Provider Refunds
    if (elements.btnRefreshProvRefunds) {
      elements.btnRefreshProvRefunds.addEventListener('click', loadProviderRefunds);
    }
    if (elements.btnCloseProvRefundModal) {
      elements.btnCloseProvRefundModal.addEventListener('click', closeProviderRefundModal);
    }
    if (elements.btnCancelProvRefund) {
      elements.btnCancelProvRefund.addEventListener('click', closeProviderRefundModal);
    }
    if (elements.formProvRefund) {
      elements.formProvRefund.addEventListener('submit', handleProviderRefundSubmit);
    }
  }

  // Phase 18C: Provider Refunds Management
  async function loadProviderRefunds() {
    if (!elements.provRefundsTbody) return;
    try {
      const res = await fetch('/api/provider/refunds/eligible');
      if (!res.ok) {
        throw new Error('Failed to fetch refund-eligible bookings');
      }
      const data = await res.json();
      const bookings = (data && data.success && Array.isArray(data.data)) ? data.data : [];
      renderProviderRefunds(bookings);
    } catch (err) {
      elements.provRefundsTbody.innerHTML = `<tr><td colspan="8" class="empty-state">Error loading bookings: ${escapeHtml(err.message)}</td></tr>`;
    }
  }

  function renderProviderRefunds(bookings) {
    if (!elements.provRefundsTbody) return;
    if (bookings.length === 0) {
      elements.provRefundsTbody.innerHTML = '<tr><td colspan="8" class="empty-state">No cancelled or unhonoured bookings eligible for refund.</td></tr>';
      return;
    }
    elements.provRefundsTbody.innerHTML = bookings.map(b => {
      const isRefunded = b.refunded || (b.refundStatus && b.refundStatus !== 'NONE');
      const actionHtml = isRefunded
        ? `<span class="badge badge-green">Refunded (₹${Number(b.refundAmount || b.cost).toFixed(2)})</span>`
        : `<button type="button" class="btn btn-gold btn-sm btn-issue-prov-refund" data-id="${escapeHtml(b.id)}" data-ref="${escapeHtml(b.bookingReference || b.id)}" data-cost="${b.cost}">Issue Refund</button>`;

      return `
          <tr>
            <td><strong style="color: var(--gold-color); font-family: monospace;">${escapeHtml(b.bookingReference || b.id)}</strong></td>
            <td><span class="badge badge-neutral">${escapeHtml(b.type)}</span></td>
            <td>${escapeHtml(b.serviceName || b.provider || '')}</td>
            <td>${escapeHtml(b.passengerNames || b.guestName || 'Traveller')}</td>
            <td><strong>₹${Number(b.cost).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong></td>
            <td><span class="badge badge-danger">${escapeHtml(b.status || 'CANCELLED')}</span></td>
            <td><span class="badge ${isRefunded ? 'badge-green' : 'badge-amber'}">${isRefunded ? 'ISSUED' : 'PENDING'}</span></td>
            <td>${actionHtml}</td>
          </tr>
        `;
    }).join('');

    elements.provRefundsTbody.querySelectorAll('.btn-issue-prov-refund').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-id');
        const ref = btn.getAttribute('data-ref');
        const cost = Number(btn.getAttribute('data-cost')) || 0;
        openProviderRefundModal(id, ref, cost);
      });
    });
  }

  function openProviderRefundModal(id, ref, cost) {
    if (!elements.modalProvRefund) return;
    if (elements.provRefundBookingId) elements.provRefundBookingId.value = id;
    if (elements.provRefundBookingRef) elements.provRefundBookingRef.textContent = ref;
    if (elements.provRefundOriginalCost) elements.provRefundOriginalCost.textContent = `₹${cost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    if (elements.provRefundAmount) {
      elements.provRefundAmount.value = cost;
      elements.provRefundAmount.max = cost;
    }
    if (elements.provRefundReason) elements.provRefundReason.value = '';
    if (elements.provRefundErrorMsg) {
      elements.provRefundErrorMsg.classList.add('hidden');
      elements.provRefundErrorMsg.textContent = '';
    }
    elements.modalProvRefund.classList.remove('hidden');
    elements.modalProvRefund.classList.add('is-open');
  }

  function closeProviderRefundModal() {
    if (elements.modalProvRefund) {
      elements.modalProvRefund.classList.add('hidden');
      elements.modalProvRefund.classList.remove('is-open');
    }
  }

  async function handleProviderRefundSubmit(e) {
    e.preventDefault();
    const bookingId = elements.provRefundBookingId.value;
    const amount = parseFloat(elements.provRefundAmount.value);
    const reason = elements.provRefundReason.value.trim();

    if (!bookingId || isNaN(amount) || amount <= 0) {
      showToast('Please enter a valid refund amount greater than 0', 'warning');
      return;
    }
    if (reason.length < 10 || reason.length > 200) {
      showToast('Reason must be between 10 and 200 characters', 'warning');
      return;
    }

    setButtonLoading(elements.btnSubmitProvRefund, true);
    if (elements.provRefundErrorMsg) elements.provRefundErrorMsg.classList.add('hidden');

    try {
      const res = await fetch('/api/provider/refunds', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ bookingId, amount, reason })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to issue refund');
      }

      showToast(`Refund of ₹${amount} issued successfully`, 'success');
      closeProviderRefundModal();
      loadProviderRefunds();
    } catch (err) {
      if (elements.provRefundErrorMsg) {
        elements.provRefundErrorMsg.textContent = err.message;
        elements.provRefundErrorMsg.classList.remove('hidden');
      }
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(elements.btnSubmitProvRefund, false);
    }
  }

  // 10. Provider Pending Requests (Phase 18E)
  async function loadProviderPendingBookings() {
    if (!elements.provPendingTbody) return;
    try {
      const res = await fetch('/api/provider/pending-bookings');
      if (!res.ok) {
        if (res.status === 403 || res.status === 401) {
          elements.provPendingTbody.innerHTML = '<tr><td colspan="8" class="empty-state">Provider login required.</td></tr>';
          return;
        }
        throw new Error(`Failed to load pending requests (HTTP ${res.status})`);
      }
      const data = await res.json();
      const bookings = (data && data.success && Array.isArray(data.data)) ? data.data : [];
      if (elements.provPendingCount) elements.provPendingCount.textContent = bookings.length;
      renderProviderPendingBookings(bookings);
    } catch (err) {
      elements.provPendingTbody.innerHTML = `<tr><td colspan="8" class="empty-state">Error loading requests: ${escapeHtml(err.message)}</td></tr>`;
    }
  }

  function renderProviderPendingBookings(bookings) {
    if (!elements.provPendingTbody) return;
    if (bookings.length === 0) {
      elements.provPendingTbody.innerHTML = '<tr><td colspan="8" class="empty-state">No pending booking requests.</td></tr>';
      return;
    }
    elements.provPendingTbody.innerHTML = bookings.map(b => {
      let expiryText = '--';
      if (b.confirmationExpiresAt) {
        const sec = Math.max(0, Math.round((new Date(b.confirmationExpiresAt).getTime() - Date.now()) / 1000));
        expiryText = `${sec}s remaining`;
      }
      const locationText = b.from && b.to ? `${b.from} → ${b.to}` : (b.to || b.from || b.operator || 'Local');
      return `
          <tr>
            <td><strong style="color: var(--gold-color); font-family: monospace;">${escapeHtml(b.bookingReference || b.id)}</strong></td>
            <td><span class="badge badge-neutral">${escapeHtml(b.type)}</span></td>
            <td>${escapeHtml(b.provider || b.operator || 'Service')}</td>
            <td>${escapeHtml(locationText)}</td>
            <td>${escapeHtml(b.passengerNames || 'Traveller')}</td>
            <td><strong>₹${Number(b.cost || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</strong></td>
            <td><span class="badge badge-amber">${escapeHtml(expiryText)}</span></td>
            <td>
              <button type="button" class="btn btn-sm btn-success btn-prov-accept" data-id="${escapeHtml(b.id)}" style="margin-right: 0.35rem;">Accept</button>
              <button type="button" class="btn btn-sm btn-danger btn-prov-reject" data-id="${escapeHtml(b.id)}">Reject</button>
            </td>
          </tr>
        `;
    }).join('');

    elements.provPendingTbody.querySelectorAll('.btn-prov-accept').forEach(btn => {
      btn.addEventListener('click', () => handleProviderAcceptBooking(btn.dataset.id));
    });
    elements.provPendingTbody.querySelectorAll('.btn-prov-reject').forEach(btn => {
      btn.addEventListener('click', () => handleProviderRejectBooking(btn.dataset.id));
    });
  }

  async function handleProviderAcceptBooking(bookingId) {
    try {
      const res = await fetch(`/api/provider/bookings/${encodeURIComponent(bookingId)}/accept`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to accept booking');
      }
      showToast('Booking accepted! Confirmation confirmed.', 'success');
      loadProviderPendingBookings();
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  async function handleProviderRejectBooking(bookingId) {
    const reason = prompt('Reason for rejecting booking request:', 'Capacity unavailable') || 'Declined by provider';
    try {
      const res = await fetch(`/api/provider/bookings/${encodeURIComponent(bookingId)}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to reject booking');
      }
      showToast('Booking request rejected.', 'info');
      loadProviderPendingBookings();
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  async function loadProviderPortal() {
    try {
      const res = await fetch('/api/provider/me');
      if (res.status === 404) {
        state.providerProfile = null;
        if (elements.panelProviderRegister) elements.panelProviderRegister.classList.remove('hidden');
        if (elements.panelProviderDashboard) elements.panelProviderDashboard.classList.add('hidden');
        return;
      }
      if (!res.ok) {
        throw new Error(`Failed to load provider profile (HTTP ${res.status})`);
      }
      const data = await res.json();
      if (!data.success || !data.data) {
        throw new Error(data.message || 'Unable to retrieve provider details');
      }

      state.providerProfile = data.data;
      if (elements.panelProviderRegister) elements.panelProviderRegister.classList.add('hidden');
      if (elements.panelProviderDashboard) elements.panelProviderDashboard.classList.remove('hidden');

      renderProviderStatusBanner(data.data);
      updateProviderTabVisibility(data.data.businessType);

      const loadPromises = [];
      const bType = (data.data.businessType || '').toUpperCase();
      if (bType === 'TRANSPORT' || bType === 'BOTH') {
        loadPromises.push(loadProviderVehicles());
        loadPromises.push(loadProviderSchedules());
        loadPromises.push(loadProviderDepartures());
      }
      if (bType === 'HOTEL' || bType === 'BOTH') {
        loadPromises.push(loadProviderHotels());
        loadPromises.push(loadProviderCabs());
      }
      loadPromises.push(loadProviderRefunds());
      loadPromises.push(loadProviderPendingBookings());
      await Promise.all(loadPromises);
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  function updateProviderTabVisibility(businessType) {
    const type = (businessType || 'TRANSPORT').toUpperCase();
    const isTransport = type === 'TRANSPORT' || type === 'BOTH';
    const isHotel = type === 'HOTEL' || type === 'BOTH';

    if (elements.btnProvTabVehicles) elements.btnProvTabVehicles.classList.toggle('hidden', !isTransport);
    if (elements.btnProvTabSchedules) elements.btnProvTabSchedules.classList.toggle('hidden', !isTransport);
    if (elements.btnProvTabDepartures) elements.btnProvTabDepartures.classList.toggle('hidden', !isTransport);

    if (elements.btnProvTabHotels) elements.btnProvTabHotels.classList.toggle('hidden', !isHotel);
    if (elements.btnProvTabCabs) elements.btnProvTabCabs.classList.toggle('hidden', !isHotel);

    if (elements.btnProvTabRefunds) elements.btnProvTabRefunds.classList.remove('hidden');
    if (elements.btnProvTabPending) elements.btnProvTabPending.classList.remove('hidden');

    const allTabs = [
      { btn: elements.btnProvTabVehicles, content: elements.provTabContentVehicles, visible: isTransport },
      { btn: elements.btnProvTabSchedules, content: elements.provTabContentSchedules, visible: isTransport },
      { btn: elements.btnProvTabDepartures, content: elements.provTabContentDepartures, visible: isTransport },
      { btn: elements.btnProvTabHotels, content: elements.provTabContentHotels, visible: isHotel },
      { btn: elements.btnProvTabCabs, content: elements.provTabContentCabs, visible: isHotel },
      { btn: elements.btnProvTabRefunds, content: elements.provTabContentRefunds, visible: true },
      { btn: elements.btnProvTabPending, content: elements.provTabContentPending, visible: true }
    ];

    const activeTab = allTabs.find(t => t.btn && t.btn.classList.contains('active'));
    if (!activeTab || !activeTab.visible) {
      allTabs.forEach(t => {
        if (t.btn) t.btn.classList.remove('active');
        if (t.content) t.content.classList.add('hidden');
      });
      const firstVisible = allTabs.find(t => t.visible && t.btn);
      if (firstVisible) {
        if (firstVisible.btn) firstVisible.btn.classList.add('active');
        if (firstVisible.content) firstVisible.content.classList.remove('hidden');
      }
    }
  }

  function renderProviderStatusBanner(p) {
    if (!elements.providerStatusBanner) return;
    let badgeClass = 'badge-neutral';
    let statusDesc = '';
    if (p.status === 'PENDING') {
      badgeClass = 'badge-amber';
      statusDesc = 'Your provider profile is currently <strong>PENDING</strong> administrator review. You can add vehicles and draft schedules now; departures will be visible to travellers once approved.';
    } else if (p.status === 'APPROVED') {
      badgeClass = 'badge-green';
      statusDesc = 'Your provider account is <strong>APPROVED</strong>. Intercity schedules and 14-day departures are published and bookable across the Odyssey network.';
    } else if (p.status === 'SUSPENDED') {
      badgeClass = 'badge-danger';
      statusDesc = `Your provider account is <strong>SUSPENDED</strong>. Reason: ${escapeHtml(p.suspensionReason || 'Account suspended by administrator')}. Active listings are hidden from search, but existing bookings remain valid.`;
    } else if (p.status === 'REJECTED') {
      badgeClass = 'badge-danger';
      statusDesc = `Your registration was <strong>REJECTED</strong>. Reason: ${escapeHtml(p.rejectionReason || 'Application rejected by administrator')}.`;
    }

    elements.providerStatusBanner.innerHTML = `
        <div class="provider-banner-card">
          <div class="provider-banner-header">
            <div>
              <h3 style="margin: 0; font-size: 1.2rem; color: #f8fafc;">${escapeHtml(p.businessName)}</h3>
              <p style="margin: 4px 0 0 0; font-size: 0.85rem; color: #94a3b8;">
                Type: <strong>${escapeHtml(p.businessType)}</strong> &bull; Base City: <strong>${escapeHtml(p.city)}</strong> &bull; Contact: <strong>${escapeHtml(p.contactEmail)}</strong> | <strong>${escapeHtml(p.contactPhone)}</strong>
              </p>
            </div>
            <div>
              <span class="badge ${badgeClass}" style="font-size: 0.85rem; padding: 0.35rem 0.75rem;">${escapeHtml(p.status)}</span>
            </div>
          </div>
          <div class="provider-banner-body" style="font-size: 0.9rem; line-height: 1.4; margin-top: 0.75rem; color: #cbd5e1;">
            ${statusDesc}
          </div>
        </div>
      `;
  }

  async function handleProviderRegisterSubmit(e) {
    e.preventDefault();
    const submitBtn = document.getElementById('btn-submit-provider-register');
    setButtonLoading(submitBtn, true);

    const businessName = document.getElementById('provider-reg-business-name').value.trim();
    const businessType = document.getElementById('provider-reg-business-type').value;
    const city = document.getElementById('provider-reg-city').value.trim();
    const contactEmail = document.getElementById('provider-reg-contact-email').value.trim();
    const contactPhone = document.getElementById('provider-reg-contact-phone').value.trim();
    const description = document.getElementById('provider-reg-desc').value.trim();

    try {
      const res = await fetch('/api/provider/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ businessName, businessType, city, contactEmail, contactPhone, description })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Provider registration failed');
      }

      if (state.currentUser) {
        state.currentUser.role = 'PROVIDER';
        updateAuthHeader();
      }
      showToast('Provider registration submitted! Account status is PENDING review.', 'success');
      await loadProviderPortal();
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  }

  // --- Provider Vehicles Management ---

  async function loadProviderVehicles() {
    if (!elements.provVehiclesTbody) return;
    try {
      const res = await fetch('/api/provider/vehicles');
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to fetch vehicles');
      }
      state.providerVehicles = data.data || [];
      if (elements.provVehiclesCount) {
        elements.provVehiclesCount.textContent = state.providerVehicles.length;
      }
      renderProviderVehicles(state.providerVehicles);
    } catch (err) {
      console.warn('Failed to load provider vehicles:', err);
      if (elements.provVehiclesTbody) {
        elements.provVehiclesTbody.innerHTML = `<tr><td colspan="5" class="empty-state" style="color: #f87171;">Unable to load fleet vehicles: ${escapeHtml(err.message)}</td></tr>`;
      }
    }
  }

  function renderProviderVehicles(vehicles) {
    if (!elements.provVehiclesTbody) return;
    if (vehicles.length === 0) {
      elements.provVehiclesTbody.innerHTML = '<tr><td colspan="5" class="empty-state">No vehicles in fleet. Click "+ Add Vehicle" to add one.</td></tr>';
      return;
    }
    elements.provVehiclesTbody.innerHTML = vehicles.map(v => `
        <tr>
          <td><strong style="color: #f8fafc;">${escapeHtml(v.vehicleNumber)}</strong><br><small style="color: #94a3b8;">${escapeHtml(v.vehicleType || '')}</small></td>
          <td><span class="badge ${v.mode === 'BUS' ? 'badge-amber' : (v.mode === 'TRAIN' ? 'badge-blue' : (v.mode === 'CAB' ? 'badge-cyan' : 'badge-purple'))}">${escapeHtml(v.mode)}</span></td>
          <td>${v.seatsTotal} seats</td>
          <td>
            <button type="button" class="btn btn-outline btn-sm btn-prov-toggle-veh" data-id="${escapeHtml(v.id)}" data-active="${v.active}">
              ${v.active ? '<span style="color: #4ade80;">Active</span>' : '<span style="color: #94a3b8;">Inactive</span>'}
            </button>
          </td>
          <td>
            <div style="display: flex; gap: 0.35rem;">
              <button type="button" class="btn btn-outline btn-sm btn-prov-edit-veh" data-id="${escapeHtml(v.id)}">Edit</button>
              <button type="button" class="btn btn-outline btn-sm btn-prov-delete-veh" data-id="${escapeHtml(v.id)}" style="color: #f87171;">Delete</button>
            </div>
          </td>
        </tr>
      `).join('');

    elements.provVehiclesTbody.querySelectorAll('.btn-prov-toggle-veh').forEach(btn => {
      btn.addEventListener('click', () => handleToggleVehicle(btn.getAttribute('data-id'), btn.getAttribute('data-active') === 'true'));
    });
    elements.provVehiclesTbody.querySelectorAll('.btn-prov-edit-veh').forEach(btn => {
      btn.addEventListener('click', () => openEditVehicleModal(btn.getAttribute('data-id')));
    });
    elements.provVehiclesTbody.querySelectorAll('.btn-prov-delete-veh').forEach(btn => {
      btn.addEventListener('click', () => handleDeleteVehicle(btn.getAttribute('data-id')));
    });
  }

  function openAddVehicleModal() {
    const bizName = (state.providerProfile && state.providerProfile.businessName) || '';
    const isCabBiz = /cab|taxi|car/i.test(bizName);
    const modeSelect = document.getElementById('prov-veh-mode');
    const typeInput = document.getElementById('prov-veh-type');
    const seatsInput = document.getElementById('prov-veh-seats');

    document.getElementById('prov-veh-id').value = '';
    document.getElementById('modal-prov-vehicle-title').textContent = 'Add Vehicle';
    document.getElementById('prov-veh-number').value = '';

    if (isCabBiz) {
      if (modeSelect) modeSelect.value = 'CAB';
      if (typeInput) {
        typeInput.placeholder = 'e.g. Sedan (Dzire, Etios) or SUV (Innova)';
        typeInput.value = 'Sedan (4 Seats)';
      }
      if (seatsInput) seatsInput.value = 4;
    } else {
      if (modeSelect) modeSelect.value = 'BUS';
      if (typeInput) {
        typeInput.placeholder = 'e.g. AC Multi-Axle Sleeper';
        typeInput.value = 'AC Multi-Axle Sleeper';
      }
      if (seatsInput) seatsInput.value = 40;
    }
    document.getElementById('prov-veh-active').checked = true;
    if (elements.modalProvVehicle) {
      elements.modalProvVehicle.classList.remove('hidden');
      elements.modalProvVehicle.classList.add('is-open');
    }
  }

  function openEditVehicleModal(id) {
    const v = state.providerVehicles.find(item => item.id === id);
    if (!v) return;
    document.getElementById('prov-veh-id').value = v.id;
    document.getElementById('modal-prov-vehicle-title').textContent = 'Edit Vehicle';
    document.getElementById('prov-veh-number').value = v.vehicleNumber;
    document.getElementById('prov-veh-mode').value = v.mode;
    document.getElementById('prov-veh-type').value = v.vehicleType || '';
    document.getElementById('prov-veh-seats').value = v.seatsTotal;
    document.getElementById('prov-veh-active').checked = v.active;
    if (elements.modalProvVehicle) {
      elements.modalProvVehicle.classList.remove('hidden');
      elements.modalProvVehicle.classList.add('is-open');
    }
  }

  function closeVehicleModal() {
    if (elements.modalProvVehicle) {
      elements.modalProvVehicle.classList.add('hidden');
      elements.modalProvVehicle.classList.remove('is-open');
    }
  }

  async function handleSaveVehicleSubmit(e) {
    e.preventDefault();
    const submitBtn = document.getElementById('btn-submit-prov-vehicle');
    setButtonLoading(submitBtn, true);

    const id = document.getElementById('prov-veh-id').value.trim();
    const vehicleNumber = document.getElementById('prov-veh-number').value.trim();
    const mode = document.getElementById('prov-veh-mode').value;
    const vehicleType = document.getElementById('prov-veh-type').value.trim() || `${mode} Express`;
    const seatsTotal = parseInt(document.getElementById('prov-veh-seats').value, 10);
    const active = document.getElementById('prov-veh-active').checked;

    const method = id ? 'PUT' : 'POST';
    const url = id ? `/api/provider/vehicles/${id}` : '/api/provider/vehicles';

    try {
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ vehicleNumber, mode, vehicleType, seatsTotal, active })
      });
      const data = await res.json();
      if (res.status === 409) {
        throw new Error(data.message || 'Conflict: cannot modify vehicle with active future bookings');
      }
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to save vehicle');
      }

      closeVehicleModal();
      showToast(id ? 'Vehicle updated successfully!' : 'Vehicle added to fleet!', 'success');
      await loadProviderVehicles();
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  }

  async function handleToggleVehicle(id, currentActive) {
    try {
      const res = await fetch(`/api/provider/vehicles/${id}/status?active=${!currentActive}`, {
        method: 'PATCH'
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to update vehicle status');
      }
      showToast(`Vehicle marked ${!currentActive ? 'Active' : 'Inactive'}`, 'info');
      await loadProviderVehicles();
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  async function handleDeleteVehicle(id) {
    if (!confirm('Are you sure you want to delete this vehicle? This action cannot be undone.')) return;
    try {
      const res = await fetch(`/api/provider/vehicles/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.status === 409) {
        throw new Error(data.message || 'Conflict: cannot delete vehicle with future bookings');
      }
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to delete vehicle');
      }
      showToast('Vehicle deleted successfully', 'success');
      await loadProviderVehicles();
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  // --- Provider Schedules Management ---

  async function loadProviderSchedules() {
    if (!elements.provSchedulesTbody) return;
    try {
      const res = await fetch('/api/provider/schedules');
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to fetch schedules');
      }
      state.providerSchedules = data.data || [];
      if (elements.provSchedulesCount) {
        elements.provSchedulesCount.textContent = state.providerSchedules.length;
      }
      renderProviderSchedules(state.providerSchedules);
    } catch (err) {
      console.warn('Failed to load provider schedules:', err);
      if (elements.provSchedulesTbody) {
        elements.provSchedulesTbody.innerHTML = `<tr><td colspan="7" class="empty-state" style="color: #f87171;">Unable to load schedules: ${escapeHtml(err.message)}</td></tr>`;
      }
    }
  }

  function renderProviderSchedules(schedules) {
    if (!elements.provSchedulesTbody) return;
    if (schedules.length === 0) {
      elements.provSchedulesTbody.innerHTML = '<tr><td colspan="7" class="empty-state">No schedules configured. Click "+ Add Schedule" to create one.</td></tr>';
      return;
    }
    elements.provSchedulesTbody.innerHTML = schedules.map(s => `
        <tr>
          <td><strong style="color: #f8fafc;">${escapeHtml(s.originCity)} &rarr; ${escapeHtml(s.destinationCity)}</strong><br><small style="color: #94a3b8;">${escapeHtml(s.originStop)} to ${escapeHtml(s.destinationStop)}</small></td>
          <td>${escapeHtml(s.vehicleNumber)}</td>
          <td>${escapeHtml(String(s.departureTime).substring(0, 5))} - ${escapeHtml(String(s.arrivalTime).substring(0, 5))}${s.arrivesNextDay ? ' <span class="badge badge-amber">+1d</span>' : ''}</td>
          <td><small style="color: #cbd5e1;">${escapeHtml(s.daysOfWeek)}</small></td>
          <td>&#8377;${Number(s.price).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
          <td>
            <button type="button" class="btn btn-outline btn-sm btn-prov-toggle-sch" data-id="${escapeHtml(s.id)}" data-active="${s.active}">
              ${s.active ? '<span style="color: #4ade80;">Active</span>' : '<span style="color: #94a3b8;">Inactive</span>'}
            </button>
          </td>
          <td>
            <div style="display: flex; gap: 0.35rem;">
              <button type="button" class="btn btn-outline btn-sm btn-prov-edit-sch" data-id="${escapeHtml(s.id)}">Edit</button>
              <button type="button" class="btn btn-outline btn-sm btn-prov-delete-sch" data-id="${escapeHtml(s.id)}" style="color: #f87171;">Delete</button>
            </div>
          </td>
        </tr>
      `).join('');

    elements.provSchedulesTbody.querySelectorAll('.btn-prov-toggle-sch').forEach(btn => {
      btn.addEventListener('click', () => handleToggleSchedule(btn.getAttribute('data-id'), btn.getAttribute('data-active') === 'true'));
    });
    elements.provSchedulesTbody.querySelectorAll('.btn-prov-edit-sch').forEach(btn => {
      btn.addEventListener('click', () => openEditScheduleModal(btn.getAttribute('data-id')));
    });
    elements.provSchedulesTbody.querySelectorAll('.btn-prov-delete-sch').forEach(btn => {
      btn.addEventListener('click', () => handleDeleteSchedule(btn.getAttribute('data-id')));
    });
  }

  function openAddScheduleModal() {
    const schErr = document.getElementById('prov-sch-error');
    if (schErr) { schErr.textContent = ''; schErr.classList.add('hidden'); }
    if (state.providerVehicles.length === 0) {
      showToast('Please add at least one vehicle before creating a schedule.', 'warning');
      return;
    }
    document.getElementById('prov-sch-id').value = '';
    document.getElementById('modal-prov-schedule-title').textContent = 'Add Schedule';

    const vehSelect = document.getElementById('prov-sch-vehicle');
    vehSelect.innerHTML = state.providerVehicles.filter(v => v.active).map(v => `
        <option value="${escapeHtml(v.id)}">${escapeHtml(v.vehicleNumber)} (${escapeHtml(v.mode)}, ${v.seatsTotal} seats)</option>
      `).join('');

    document.getElementById('prov-sch-origin').value = '';
    document.getElementById('prov-sch-dest').value = '';
    document.getElementById('prov-sch-origin-stop').value = '';
    document.getElementById('prov-sch-dest-stop').value = '';
    document.getElementById('prov-sch-dep-time').value = '08:00';
    document.getElementById('prov-sch-arr-time').value = '14:00';
    document.getElementById('prov-sch-next-day').checked = false;
    document.getElementById('prov-sch-price').value = '650.00';
    document.getElementById('prov-sch-seats-offered').value = '';

    const today = new Date();
    const todayStr = today.toISOString().substring(0, 10);
    const future = new Date();
    future.setDate(future.getDate() + 30);
    const futureStr = future.toISOString().substring(0, 10);

    const validFromInput = document.getElementById('prov-sch-valid-from');
    const validToInput = document.getElementById('prov-sch-valid-to');
    if (validFromInput) validFromInput.value = todayStr;
    if (validToInput) validToInput.value = futureStr;

    document.querySelectorAll('.prov-day-chk').forEach(chk => { chk.checked = true; });

    if (elements.modalProvSchedule) {
      elements.modalProvSchedule.classList.remove('hidden');
      elements.modalProvSchedule.classList.add('is-open');
    }
  }

  function openEditScheduleModal(id) {
    const schErr = document.getElementById('prov-sch-error');
    if (schErr) { schErr.textContent = ''; schErr.classList.add('hidden'); }
    const s = state.providerSchedules.find(item => item.id === id);
    if (!s) return;
    document.getElementById('prov-sch-id').value = s.id;
    document.getElementById('modal-prov-schedule-title').textContent = 'Edit Schedule';

    const vehSelect = document.getElementById('prov-sch-vehicle');
    vehSelect.innerHTML = state.providerVehicles.map(v => `
        <option value="${escapeHtml(v.id)}" ${v.id === s.vehicleId ? 'selected' : ''}>${escapeHtml(v.vehicleNumber)} (${escapeHtml(v.mode)}, ${v.seatsTotal} seats)</option>
      `).join('');

    document.getElementById('prov-sch-origin').value = s.originCity;
    document.getElementById('prov-sch-dest').value = s.destinationCity;
    document.getElementById('prov-sch-origin-stop').value = s.originStop;
    document.getElementById('prov-sch-dest-stop').value = s.destinationStop;
    document.getElementById('prov-sch-dep-time').value = String(s.departureTime).substring(0, 5);
    document.getElementById('prov-sch-arr-time').value = String(s.arrivalTime).substring(0, 5);
    document.getElementById('prov-sch-next-day').checked = !!s.arrivesNextDay;
    document.getElementById('prov-sch-price').value = s.price;
    document.getElementById('prov-sch-seats-offered').value = s.seatsOffered;

    const validFromInput = document.getElementById('prov-sch-valid-from');
    const validToInput = document.getElementById('prov-sch-valid-to');
    if (validFromInput && s.validFrom) validFromInput.value = s.validFrom;
    if (validToInput && s.validTo) validToInput.value = s.validTo;

    const daysArr = (s.daysOfWeek || '').split(',');
    document.querySelectorAll('.prov-day-chk').forEach(chk => {
      chk.checked = daysArr.includes(chk.value);
    });

    if (elements.modalProvSchedule) {
      elements.modalProvSchedule.classList.remove('hidden');
      elements.modalProvSchedule.classList.add('is-open');
    }
  }

  function closeScheduleModal() {
    if (elements.modalProvSchedule) {
      elements.modalProvSchedule.classList.add('hidden');
      elements.modalProvSchedule.classList.remove('is-open');
    }
  }

  async function handleSaveScheduleSubmit(e) {
    e.preventDefault();
    const schErr = document.getElementById('prov-sch-error');
    if (schErr) { schErr.textContent = ''; schErr.classList.add('hidden'); }
    const submitBtn = document.getElementById('btn-submit-prov-schedule');
    setButtonLoading(submitBtn, true);

    const id = document.getElementById('prov-sch-id').value.trim();
    const vehicleId = document.getElementById('prov-sch-vehicle').value;
    const originCity = document.getElementById('prov-sch-origin').value.trim();
    const destinationCity = document.getElementById('prov-sch-dest').value.trim();
    const originStopVal = document.getElementById('prov-sch-origin-stop').value.trim();
    const destinationStopVal = document.getElementById('prov-sch-dest-stop').value.trim();
    const originStop = originStopVal || (originCity ? `${originCity} Central` : 'Origin Central');
    const destinationStop = destinationStopVal || (destinationCity ? `${destinationCity} Central` : 'Destination Central');
    const departureTime = document.getElementById('prov-sch-dep-time').value;
    const arrivalTime = document.getElementById('prov-sch-arr-time').value;
    const arrivesNextDay = document.getElementById('prov-sch-next-day').checked;
    const price = parseFloat(document.getElementById('prov-sch-price').value);

    const seatsOfferedVal = document.getElementById('prov-sch-seats-offered').value;
    const selectedVehicle = state.providerVehicles.find(v => v.id === vehicleId);
    const seatsOffered = (seatsOfferedVal && parseInt(seatsOfferedVal, 10) > 0)
      ? parseInt(seatsOfferedVal, 10)
      : (selectedVehicle ? selectedVehicle.seatsTotal : 40);

    const days = [];
    document.querySelectorAll('.prov-day-chk:checked').forEach(c => days.push(c.value));
    const daysOfWeek = days.join(',');

    const today = new Date().toISOString().substring(0, 10);
    const validFrom = (document.getElementById('prov-sch-valid-from') && document.getElementById('prov-sch-valid-from').value) || today;
    const validTo = (document.getElementById('prov-sch-valid-to') && document.getElementById('prov-sch-valid-to').value) || today;

    const payload = {
      vehicleId, originCity, destinationCity, originStop, destinationStop,
      departureTime, arrivalTime, arrivesNextDay, price, seatsOffered,
      daysOfWeek, validFrom, validTo, active: true
    };

    const method = id ? 'PUT' : 'POST';
    const url = id ? `/api/provider/schedules/${id}` : '/api/provider/schedules';

    try {
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        let errText = data.message || 'Failed to save schedule';
        if (data.details && Array.isArray(data.details) && data.details.length > 0) {
          errText = data.details.join(', ');
        }
        throw new Error(errText);
      }

      closeScheduleModal();
      showToast(id ? 'Schedule updated (unbooked departures re-materialised)!' : 'Schedule created and 14-day departures materialised!', 'success');
      await Promise.all([
        loadProviderSchedules(),
        loadProviderDepartures()
      ]);
    } catch (err) {
      if (schErr) {
        schErr.textContent = err.message;
        schErr.classList.remove('hidden');
      }
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  }

  async function handleToggleSchedule(id, currentActive) {
    try {
      const res = await fetch(`/api/provider/schedules/${id}/status?active=${!currentActive}`, {
        method: 'PATCH'
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to update schedule status');
      }
      showToast(`Schedule marked ${!currentActive ? 'Active' : 'Inactive'}`, 'info');
      await Promise.all([
        loadProviderSchedules(),
        loadProviderDepartures()
      ]);
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  async function handleDeleteSchedule(id) {
    if (!confirm('Are you sure you want to delete this schedule?')) return;
    try {
      const res = await fetch(`/api/provider/schedules/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to delete schedule');
      }
      showToast('Schedule deleted successfully', 'success');
      await Promise.all([
        loadProviderSchedules(),
        loadProviderDepartures()
      ]);
    } catch (err) {
      showToast(err.message, 'danger');
    }
  }

  // --- Provider Departures Management & Status Reporting ---

  async function loadProviderDepartures() {
    if (!elements.provDeparturesTbody) return;
    try {
      const res = await fetch('/api/provider/departures');
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to fetch departures');
      }
      state.providerDepartures = data.data || [];
      if (elements.provDeparturesCount) {
        elements.provDeparturesCount.textContent = state.providerDepartures.length;
      }
      renderProviderDepartures(state.providerDepartures);
    } catch (err) {
      console.warn('Failed to load provider departures:', err);
      if (elements.provDeparturesTbody) {
        elements.provDeparturesTbody.innerHTML = `<tr><td colspan="7" class="empty-state" style="color: #f87171;">Unable to load departures: ${escapeHtml(err.message)}</td></tr>`;
      }
    }
  }

  function renderProviderDepartures(departures) {
    if (!elements.provDeparturesTbody) return;
    if (departures.length === 0) {
      elements.provDeparturesTbody.innerHTML = '<tr><td colspan="7" class="empty-state">No upcoming departures materialised.</td></tr>';
      return;
    }
    elements.provDeparturesTbody.innerHTML = departures.map(d => {
      let statusBadge = 'badge-green';
      let statusText = d.status || 'SCHEDULED';
      if (d.status === 'DELAYED') {
        statusBadge = 'badge-amber';
        statusText = `DELAYED (+${d.delayMinutes || 0}m)`;
      } else if (d.status === 'CANCELLED') {
        statusBadge = 'badge-danger';
      }

      const booked = (d.seatsTotal || 0) - (d.seatsAvailable || 0);
      const isTransportMode = d.mode === 'BUS' || d.mode === 'TRAIN' || d.mode === 'FLIGHT';

      return `
          <tr>
            <td><strong style="color: #f8fafc;">${escapeHtml(d.travelDate)}</strong></td>
            <td>${escapeHtml(d.originCity)} &rarr; ${escapeHtml(d.destinationCity)}</td>
            <td>${escapeHtml(d.vehicleNumber)}</td>
            <td>${escapeHtml(String(d.departureTime).substring(0, 5))} - ${escapeHtml(String(d.arrivalTime).substring(0, 5))}</td>
            <td><span class="badge ${booked > 0 ? 'badge-amber' : 'badge-neutral'}">${booked} / ${d.seatsTotal}</span></td>
            <td><span class="badge ${statusBadge}">${escapeHtml(statusText)}</span></td>
            <td>
              <div style="display: flex; gap: 0.35rem; flex-wrap: wrap;">
                <button type="button" class="btn btn-outline btn-sm btn-prov-report-delay" data-id="${escapeHtml(d.id)}">Report Delay</button>
                <button type="button" class="btn btn-outline btn-sm btn-prov-report-cancel" data-id="${escapeHtml(d.id)}" style="color: #ef4444; border-color: rgba(239, 68, 68, 0.4);">Report Cancel</button>
                ${isTransportMode ? `<button type="button" class="btn btn-outline btn-sm btn-prov-offer-alt" data-id="${escapeHtml(d.id)}" style="color: #10b981; border-color: rgba(16, 185, 129, 0.4);">Offer Alternative</button>` : ''}
                <button type="button" class="btn btn-outline btn-sm btn-prov-view-manifest" data-id="${escapeHtml(d.id)}">Manifest (${booked})</button>
              </div>
            </td>
          </tr>
        `;
    }).join('');

    elements.provDeparturesTbody.querySelectorAll('.btn-prov-report-delay').forEach(btn => {
      btn.addEventListener('click', () => openStatusModal(btn.getAttribute('data-id'), 'DELAYED'));
    });
    elements.provDeparturesTbody.querySelectorAll('.btn-prov-report-cancel').forEach(btn => {
      btn.addEventListener('click', () => openStatusModal(btn.getAttribute('data-id'), 'CANCELLED'));
    });
    elements.provDeparturesTbody.querySelectorAll('.btn-prov-offer-alt').forEach(btn => {
      btn.addEventListener('click', () => openOfferAlternativeModal(btn.getAttribute('data-id')));
    });
    elements.provDeparturesTbody.querySelectorAll('.btn-prov-view-manifest').forEach(btn => {
      btn.addEventListener('click', () => openBookingsModal(btn.getAttribute('data-id')));
    });
  }

  function openStatusModal(id, preselectedStatus = null) {
    const dep = state.providerDepartures.find(d => d.id === id);
    if (!dep) return;
    if (elements.provStatusDepId) elements.provStatusDepId.value = dep.id;
    if (elements.provStatusDepSummary) {
      elements.provStatusDepSummary.innerHTML = `
          <strong>Departure:</strong> ${escapeHtml(dep.travelDate)} &bull; ${escapeHtml(dep.originCity)} &rarr; ${escapeHtml(dep.destinationCity)}<br>
          <strong>Vehicle:</strong> ${escapeHtml(dep.vehicleNumber)} &bull; <strong>Current Status:</strong> ${escapeHtml(dep.status)}
        `;
    }
    const targetStatus = preselectedStatus || (dep.status === 'CANCELLED' ? 'CANCELLED' : 'DELAYED');
    if (elements.provStatusState) elements.provStatusState.value = targetStatus;
    if (elements.provStatusDelay) elements.provStatusDelay.value = dep.delayMinutes || 30;
    if (elements.provStatusReason) elements.provStatusReason.value = dep.statusReason || (targetStatus === 'CANCELLED' ? 'Operational cancellation due to technical scheduling' : '');
    if (elements.provStatusDelayGroup) {
      elements.provStatusDelayGroup.classList.toggle('hidden', targetStatus !== 'DELAYED');
    }

    if (elements.modalProvStatus) {
      elements.modalProvStatus.classList.remove('hidden');
      elements.modalProvStatus.classList.add('is-open');
    }
  }

  function closeStatusModal() {
    if (elements.modalProvStatus) {
      elements.modalProvStatus.classList.add('hidden');
      elements.modalProvStatus.classList.remove('is-open');
    }
  }

  async function handleSaveStatusSubmit(e) {
    e.preventDefault();
    const submitBtn = document.getElementById('btn-submit-prov-status');
    setButtonLoading(submitBtn, true);

    const depId = elements.provStatusDepId.value.trim();
    const stateVal = elements.provStatusState.value;
    const delayMinutes = stateVal === 'DELAYED' ? parseInt(elements.provStatusDelay.value, 10) : 0;
    const reason = elements.provStatusReason.value.trim();

    try {
      const res = await fetch(`/api/provider/departures/${depId}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ state: stateVal, delayMinutes, reason })
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to update departure status');
      }

      closeStatusModal();
      showToast('Departure status updated! Disruption recorded for affected travellers.', 'warning');
      await loadProviderDepartures();
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  }

  function openOfferAlternativeModal(depId) {
    const dep = (state.providerDepartures || []).find(d => d.id === depId);
    if (!dep) return;
    if (elements.provOfferAltDepId) elements.provOfferAltDepId.value = dep.id;
    if (elements.provOfferAltDepSummary) {
      elements.provOfferAltDepSummary.innerHTML = `
          <strong>Primary Problem Departure:</strong> ${escapeHtml(dep.travelDate)} &bull; ${escapeHtml(dep.originCity)} &rarr; ${escapeHtml(dep.destinationCity)}<br>
          <strong>Vehicle:</strong> ${escapeHtml(dep.vehicleNumber)} &bull; <strong>Time:</strong> ${escapeHtml(String(dep.departureTime).substring(0, 5))} - ${escapeHtml(String(dep.arrivalTime).substring(0, 5))}<br>
          <em>Offering a replacement will mark this departure as CANCELLED and suggest the alternative vehicle to affected travellers.</em>
        `;
    }

    if (elements.provOfferAltSelect) {
      elements.provOfferAltSelect.innerHTML = '<option value="">-- Choose replacement vehicle --</option>';

      // 1. Existing departures on the same route & date with available seats
      const existingDepartures = (state.providerDepartures || []).filter(other =>
        other.id !== dep.id &&
        other.travelDate === dep.travelDate &&
        String(other.originCity).toLowerCase() === String(dep.originCity).toLowerCase() &&
        String(other.destinationCity).toLowerCase() === String(dep.destinationCity).toLowerCase() &&
        (other.seatsAvailable || 0) > 0 &&
        other.status !== 'CANCELLED'
      );

      // 2. Active fleet vehicles (spare / backup buses) registered to this provider
      const fleetVehicles = (state.providerVehicles || []).filter(v =>
        v.active !== false &&
        String(v.vehicleNumber).toLowerCase() !== String(dep.vehicleNumber).toLowerCase()
      );

      let optionCount = 0;

      if (existingDepartures.length > 0) {
        const groupDep = document.createElement('optgroup');
        groupDep.label = `Existing Departures on Same Route (${dep.travelDate})`;
        existingDepartures.forEach(m => {
          const opt = document.createElement('option');
          opt.value = m.id;
          opt.textContent = `${m.vehicleNumber} (${String(m.departureTime).substring(0, 5)} - ${String(m.arrivalTime).substring(0, 5)}) — ${m.seatsAvailable} seats available`;
          groupDep.appendChild(opt);
          optionCount++;
        });
        elements.provOfferAltSelect.appendChild(groupDep);
      }

      if (fleetVehicles.length > 0) {
        const groupFleet = document.createElement('optgroup');
        groupFleet.label = 'Deploy Replacement Vehicle from Fleet';
        fleetVehicles.forEach(v => {
          const opt = document.createElement('option');
          opt.value = `VEHICLE:${v.id}`;
          opt.textContent = `${v.vehicleNumber} (${v.vehicleType || 'Bus'}, ${v.seatsTotal} seats) — Deploy on this route`;
          groupFleet.appendChild(opt);
          optionCount++;
        });
        elements.provOfferAltSelect.appendChild(groupFleet);
      }

      // 3. Option to deploy a new backup vehicle right here
      const modeLabel = dep.mode === 'CAB' ? 'Cab' : (dep.mode === 'TRAIN' ? 'Train' : (dep.mode === 'FLIGHT' ? 'Flight' : 'Bus'));
      const groupNew = document.createElement('optgroup');
      groupNew.label = `Deploy Other / New Backup ${modeLabel}`;
      const optNew = document.createElement('option');
      optNew.value = 'NEW_VEHICLE';
      optNew.textContent = `+ Deploy New Backup ${modeLabel} to Route...`;
      groupNew.appendChild(optNew);
      elements.provOfferAltSelect.appendChild(groupNew);

      // Pre-fill default seats for new backup vehicle
      if (elements.provOfferAltNewSeats) {
        elements.provOfferAltNewSeats.value = dep.seatsTotal || (dep.mode === 'CAB' ? 4 : 40);
      }
      if (elements.provOfferAltNewNumber) {
        elements.provOfferAltNewNumber.value = '';
      }
      if (elements.provOfferAltNewType) {
        elements.provOfferAltNewType.value = `${dep.mode || 'Vehicle'} Backup Vehicle`;
      }

      // If no existing departures and no fleet vehicles, auto-select NEW_VEHICLE and show the input fields
      if (optionCount === 0) {
        elements.provOfferAltSelect.value = 'NEW_VEHICLE';
        if (elements.provOfferAltNewVehicleSection) elements.provOfferAltNewVehicleSection.classList.remove('hidden');
      } else {
        if (elements.provOfferAltNewVehicleSection) elements.provOfferAltNewVehicleSection.classList.add('hidden');
      }

      if (elements.btnSubmitProvOfferAlt) elements.btnSubmitProvOfferAlt.disabled = false;
    }

    if (elements.provOfferAltReason) elements.provOfferAltReason.value = '';

    if (elements.modalProvOfferAlt) {
      elements.modalProvOfferAlt.classList.remove('hidden');
      elements.modalProvOfferAlt.classList.add('is-open');
    }
  }

  function closeOfferAlternativeModal() {
    if (elements.modalProvOfferAlt) {
      elements.modalProvOfferAlt.classList.add('hidden');
      elements.modalProvOfferAlt.classList.remove('is-open');
    }
  }

  async function handleOfferAlternativeSubmit(e) {
    e.preventDefault();
    const submitBtn = elements.btnSubmitProvOfferAlt || document.getElementById('btn-submit-prov-offer-alt');
    setButtonLoading(submitBtn, true);

    const depId = elements.provOfferAltDepId.value.trim();
    const altValue = elements.provOfferAltSelect.value.trim();
    const reason = elements.provOfferAltReason ? elements.provOfferAltReason.value.trim() : '';

    if (!altValue) {
      showToast('Please select an alternative departure or backup vehicle', 'danger');
      setButtonLoading(submitBtn, false);
      return;
    }

    const payload = {
      reason: reason
    };

    if (altValue === 'NEW_VEHICLE') {
      const newNum = elements.provOfferAltNewNumber ? elements.provOfferAltNewNumber.value.trim() : '';
      const newSeats = elements.provOfferAltNewSeats ? parseInt(elements.provOfferAltNewSeats.value, 10) : 40;
      const newType = elements.provOfferAltNewType ? elements.provOfferAltNewType.value.trim() : 'Bus';
      if (!newNum) {
        showToast('Please enter the replacement vehicle number', 'danger');
        setButtonLoading(submitBtn, false);
        return;
      }
      payload.alternativeDepartureId = 'NEW_VEHICLE';
      payload.replacementVehicleNumber = newNum;
      payload.replacementSeatsTotal = newSeats;
      payload.replacementVehicleType = newType;
    } else if (altValue.startsWith('VEHICLE:')) {
      payload.alternativeDepartureId = altValue;
      payload.alternativeVehicleId = altValue.substring(8);
    } else {
      payload.alternativeDepartureId = altValue;
    }

    try {
      const res = await fetch(`/api/provider/departures/${encodeURIComponent(depId)}/offer-alternative`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to offer alternative vehicle');
      }

      closeOfferAlternativeModal();
      showToast('Alternative vehicle offered successfully! Injected into recovery pipeline for affected travellers.', 'success');
      await Promise.all([
        loadProviderDepartures(),
        loadProviderVehicles()
      ]);
    } catch (err) {
      showToast(err.message, 'danger');
    } finally {
      setButtonLoading(submitBtn, false);
    }
  }

  async function openBookingsModal(depId) {
    const dep = (state.providerDepartures || []).find(d => d.id === depId);
    if (elements.provBookingsDepSummary && dep) {
      const booked = (dep.seatsTotal || 0) - (dep.seatsAvailable || 0);
      elements.provBookingsDepSummary.innerHTML = `
          <strong>Departure:</strong> ${escapeHtml(dep.travelDate)} &bull; ${escapeHtml(dep.originCity)} &rarr; ${escapeHtml(dep.destinationCity)} (${escapeHtml(dep.vehicleNumber)})<br>
          <strong>Total Seats Booked Today:</strong> <span class="badge ${booked > 0 ? 'badge-amber' : 'badge-neutral'}">${booked} / ${dep.seatsTotal} seats booked</span> &bull; <strong>Available:</strong> ${dep.seatsAvailable} seats
        `;
    }

    if (elements.provBookingsTbody) {
      elements.provBookingsTbody.innerHTML = '<tr><td colspan="5" class="empty-state">Loading manifest...</td></tr>';
    }
    if (elements.provCancellationsTbody) {
      elements.provCancellationsTbody.innerHTML = '<tr><td colspan="5" class="empty-state">Loading cancellations...</td></tr>';
    }

    if (elements.modalProvBookings) {
      elements.modalProvBookings.classList.remove('hidden');
      elements.modalProvBookings.classList.add('is-open');
    }

    try {
      const res = await fetch(`/api/provider/departures/${depId}/bookings`);
      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Failed to load departure manifest');
      }
      const bookings = data.data || [];
      const confirmedBookings = bookings.filter(b => b.status !== 'CANCELLED');
      const cancelledBookings = bookings.filter(b => b.status === 'CANCELLED');

      if (elements.provBookingsTbody) {
        if (confirmedBookings.length === 0) {
          elements.provBookingsTbody.innerHTML = '<tr><td colspan="5" class="empty-state">No active bookings on this departure.</td></tr>';
        } else {
          elements.provBookingsTbody.innerHTML = confirmedBookings.map(b => `
              <tr>
                <td><span class="mono-tag">${escapeHtml(b.bookingReference || '-')}</span></td>
                <td><strong>${escapeHtml(b.passengerNames || 'Passenger')}</strong></td>
                <td>${escapeHtml(b.passengerNames || '-')}</td>
                <td><span class="badge badge-neutral">${escapeHtml(b.seatNumbers || '-')}</span></td>
                <td><span class="badge ${getStatusBadgeClass(b.status)}">${escapeHtml(formatStatus(b.status))}</span></td>
              </tr>
            `).join('');
        }
      }

      if (elements.provCancellationsTbody) {
        if (cancelledBookings.length === 0) {
          elements.provCancellationsTbody.innerHTML = '<tr><td colspan="5" class="empty-state">No cancelled bookings for this departure.</td></tr>';
        } else {
          elements.provCancellationsTbody.innerHTML = cancelledBookings.map(b => `
              <tr>
                <td><span class="mono-tag">${escapeHtml(b.bookingReference || '-')}</span></td>
                <td><strong>${escapeHtml(b.passengerNames || 'Passenger')}</strong></td>
                <td><span class="badge badge-warning">${escapeHtml(b.seatNumbers || '-')}</span></td>
                <td>${b.cancellationTimestamp ? formatDateTime(b.cancellationTimestamp) : 'Cancelled'}</td>
                <td><span class="badge badge-danger">CANCELLED</span></td>
              </tr>
            `).join('');
        }
      }
    } catch (err) {
      if (elements.provBookingsTbody) {
... [truncated for diff preview]