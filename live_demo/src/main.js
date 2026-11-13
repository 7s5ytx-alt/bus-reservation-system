import './style.css';
import './route-results.css';

// VITE_API_BASE can override this value for staging or another API host.
// Development uses the local Java server; the published site uses Render.
const API_BASE = import.meta.env.VITE_API_BASE || (import.meta.env.DEV
  ? 'http://localhost:8080/api'
  : 'https://bus-reservation-api-uqnc.onrender.com/api');
const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
const tripDetails = {
  101: { departure: '21:30', arrival: '05:45', duration: '8h 15m', fare: 899, type: 'AC Sleeper', rating: '4.6' },
  102: { departure: '22:00', arrival: '06:30', duration: '8h 30m', fare: 1199, type: 'Volvo Multi-Axle', rating: '4.7' },
  103: { departure: '20:45', arrival: '07:15', duration: '10h 30m', fare: 1049, type: 'AC Sleeper', rating: '4.5' },
  104: { departure: '22:15', arrival: '06:30', duration: '8h 15m', fare: 749, type: 'AC Seater Sleeper', rating: '4.4' },
  105: { departure: '21:00', arrival: '05:30', duration: '8h 30m', fare: 1099, type: 'AC Sleeper', rating: '4.6' },
  106: { departure: '06:15', arrival: '12:00', duration: '5h 45m', fare: 599, type: 'Volvo AC Seater', rating: '4.7' },
  107: { departure: '07:30', arrival: '11:15', duration: '3h 45m', fare: 499, type: 'AC Seater', rating: '4.5' },
  108: { departure: '22:30', arrival: '04:45', duration: '6h 15m', fare: 699, type: 'AC Sleeper', rating: '4.4' }
};
const state = {
  buses: [
    { busNo: 101, name: 'Kaveri Express', src: 'Bangalore', dest: 'Chennai', totalSeats: 24, ...tripDetails[101] },
    { busNo: 102, name: 'Royal Volvo', src: 'Hyderabad', dest: 'Bangalore', totalSeats: 28, ...tripDetails[102] },
    { busNo: 103, name: 'Night Rider', src: 'Mumbai', dest: 'Goa', totalSeats: 20, ...tripDetails[103] },
    { busNo: 104, name: 'Tamil Nadu Express', src: 'Chennai', dest: 'Coimbatore', totalSeats: 32, ...tripDetails[104] },
    { busNo: 105, name: 'Deccan Sleeper', src: 'Bangalore', dest: 'Hyderabad', totalSeats: 30, ...tripDetails[105] },
    { busNo: 106, name: 'Pink City Volvo', src: 'Delhi', dest: 'Jaipur', totalSeats: 36, ...tripDetails[106] },
    { busNo: 107, name: 'Western Ghats Travels', src: 'Pune', dest: 'Mumbai', totalSeats: 34, ...tripDetails[107] },
    { busNo: 108, name: 'Coastal Connect', src: 'Vijayawada', dest: 'Hyderabad', totalSeats: 30, ...tripDetails[108] }
  ], selectedBusNo: 101, selectedSeat: null, seats: [], connected: false, searched: false, travelDate: tomorrow, passengerName: '', lastSyncedAt: null, searchFrom: 'Bangalore', searchTo: 'Chennai', coupon: '', couponMessage: '', paymentOpen: false, paymentMethod: 'upi', bookingStatus: 'payment', ticket: null, cityPicker: '', cityFilter: '', tracking: null
};
const coupons = {
  WELCOME10: { label: '10% off up to ₹150', discount: (fare) => Math.min(Math.round(fare * 0.10), 150) },
  TRIPLY100: { label: '₹100 off your ride', discount: () => 100 },
  SAVE50: { label: '₹50 instant discount', discount: () => 50 }
};
const escapeHtml = (value) => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
const activeBus = () => state.buses.find((bus) => bus.busNo === state.selectedBusNo) ?? state.buses[0];
const matchingBuses = () => state.buses.filter((bus) =>
  bus.src.toLowerCase() === state.searchFrom.trim().toLowerCase()
  && bus.dest.toLowerCase() === state.searchTo.trim().toLowerCase()
);
const fallbackSeats = (bus) => Array.from({ length: bus.totalSeats }, (_, index) => ({ seatNo: index + 1, isBooked: false, passenger: '' }));
const discountFor = (fare) => coupons[state.coupon]?.discount(fare) ?? 0;
const bookingTotal = (fare) => Math.max(0, fare - discountFor(fare));
const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function refreshAvailability() {
  const bus = activeBus();
  try {
    const response = await fetch(`${API_BASE}/seats?busNumber=${bus.busNo}`);
    if (!response.ok) throw new Error();
    state.seats = (await response.json()).seats;
    if (state.selectedSeat && state.seats.find((seat) => seat.seatNo === state.selectedSeat)?.isBooked) {
      state.selectedSeat = null;
    }
    state.connected = true;
    state.lastSyncedAt = new Date();
  } catch { state.seats = fallbackSeats(bus); state.connected = false; state.lastSyncedAt = null; }
}

async function refreshTracking() {
  try {
    const response = await fetch(`${API_BASE}/track?busNumber=${activeBus().busNo}`);
    if (!response.ok) throw new Error();
    state.tracking = await response.json();
  } catch { state.tracking = null; }
}

async function loadBuses() {
  try {
    const response = await fetch(`${API_BASE}/buses`);
    if (!response.ok) throw new Error();
    state.buses = (await response.json()).map((bus) => ({ ...(tripDetails[bus.busNo] ?? { rating: '4.3' }), ...bus }));
  } catch { /* Preview mode uses local sample data. */ }
  await refreshAvailability();
  await refreshTracking();
}

const availableSeats = () => state.seats.filter((seat) => !seat.isBooked).length;

function renderCityPicker(field, label, value, cities) {
  const open = state.cityPicker === field;
  const matches = cities.filter((city) => city.toLowerCase().includes(state.cityFilter.toLowerCase()));
  return `<div class="city-picker ${open ? 'open' : ''}" data-picker="${field}"><label>${label}</label><button type="button" class="city-trigger" data-picker="${field}" aria-haspopup="listbox" aria-expanded="${open}"><strong>${escapeHtml(value)}</strong><small>${label === 'From' ? 'Departure city' : 'Arrival city'}</small><b>⌄</b></button>${open ? `<div class="city-popover"><div class="city-search-wrap"><span>⌕</span><input class="city-filter" placeholder="Search city" value="${escapeHtml(state.cityFilter)}" autocomplete="off" aria-label="Search ${label.toLowerCase()} city"></div><div class="city-options" role="listbox">${matches.length ? matches.map((city) => `<button type="button" role="option" aria-selected="${city === value}" class="${city === value ? 'selected' : ''}" data-city="${escapeHtml(city)}" data-field="${field}"><span>${escapeHtml(city)}</span>${city === value ? '<b>✓</b>' : ''}</button>`).join('') : '<span class="no-cities">No matching city</span>'}</div></div>` : ''}</div>`;
}

function renderService(bus) {
  const chosen = bus.busNo === state.selectedBusNo;
  const available = chosen ? availableSeats() : (bus.availableSeats ?? bus.totalSeats);
  return `<article class="service-card ${chosen ? 'selected' : ''}" data-bus="${bus.busNo}">
    <div class="service-main"><div class="operator"><div class="operator-logo">${escapeHtml(bus.name[0])}</div><div><h3>${escapeHtml(bus.name)}</h3><p>${escapeHtml(bus.type)} · ${bus.rating} <b>★</b></p></div></div>
      <div class="time"><strong>${bus.departure}</strong><span>${escapeHtml(bus.src)}</span></div><div class="journey"><i></i><small>${bus.duration}</small></div><div class="time"><strong>${bus.arrival}</strong><span>${escapeHtml(bus.dest)}</span></div></div>
    <div class="service-side"><span>${available} seats left</span><strong>${money.format(bus.fare)}</strong><small>per passenger</small><button type="button">${chosen ? 'Selected' : 'Select seats'}</button></div></article>`;
}

function renderSeatMap() {
  return state.seats.map((seat, index) => {
    const aisle = index === Math.ceil(state.seats.length / 2) ? '<div class="driver">Driver</div>' : '';
    return `${aisle}<button type="button" class="seat ${seat.isBooked ? 'booked' : ''} ${seat.seatNo === state.selectedSeat ? 'chosen' : ''}" data-seat="${seat.seatNo}" ${seat.isBooked ? 'disabled' : ''} aria-label="Seat ${seat.seatNo}${seat.isBooked ? ', booked' : ', available'}">${seat.seatNo}</button>`;
  }).join('');
}

function renderPanel() {
  const bus = activeBus(); const selected = state.seats.find((seat) => seat.seatNo === state.selectedSeat);
  const fare = selected ? bus.fare : 0;
  const discount = discountFor(fare);
  const liveTrack = state.tracking?.available;
  const updated = liveTrack ? new Date(state.tracking.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
  return `<div class="panel-heading"><div><p class="eyebrow">Choose your seat</p><h2>${escapeHtml(bus.name)}</h2></div><span>${escapeHtml(bus.type)}</span></div>
    <section class="tracking-card ${liveTrack ? 'live' : ''}"><div class="tracking-head"><div><b>${liveTrack ? '● Live bus location' : '○ Driver GPS offline'}</b><small>${liveTrack ? `Updated ${updated} · ${Math.round(state.tracking.accuracy || 0)}m accuracy` : `Driver ${escapeHtml(bus.driverName)} has not started sharing yet`}</small></div><button type="button" id="refresh-track">Refresh</button></div><div class="route-map"><span>${escapeHtml(bus.src)}</span><i></i><em class="bus-marker">🚌</em><span>${escapeHtml(bus.dest)}</span></div></section>
    <div class="legend"><i></i> Available <i class="key-selected"></i> Selected <i class="key-booked"></i> Booked</div>
    <div class="bus-shell"><div class="wheel">◔</div><div class="seat-grid">${renderSeatMap()}</div></div>
    <form id="checkout-form" class="checkout"><label for="passenger">Passenger details</label><input id="passenger" placeholder="Full name as on ID" maxlength="100" value="${escapeHtml(state.passengerName)}" required ${selected ? '' : 'disabled'}>
      <div class="coupon-row"><input id="coupon" placeholder="Coupon code" value="${escapeHtml(state.coupon)}" ${selected ? '' : 'disabled'}><button type="button" id="apply-coupon" ${selected ? '' : 'disabled'}>Apply</button></div><p class="coupon-message ${state.couponMessage.startsWith('Applied') ? 'valid' : ''}">${escapeHtml(state.couponMessage || 'Try WELCOME10, TRIPLY100 or SAVE50')}</p>
      <div class="price-lines"><div><span>${selected ? `Seat ${selected.seatNo}` : 'Select a seat'}</span><strong>${selected ? money.format(fare) : '—'}</strong></div>${discount ? `<div class="discount"><span>Coupon discount</span><strong>−${money.format(discount)}</strong></div>` : ''}<div class="total"><span>Total payable</span><strong>${selected ? money.format(bookingTotal(fare)) : '—'}</strong></div></div>
      <button type="submit" ${selected ? '' : 'disabled'}>Review & pay <b>→</b></button><p id="booking-message">${state.connected ? 'Secure demo checkout · no real payment is collected.' : 'Preview mode · start the Java server for a live reservation.'}</p></form>`;
}

function renderPaymentModal() {
  if (!state.paymentOpen) return '';
  const bus = activeBus(); const total = bookingTotal(bus.fare);
  const details = `<div class="payment-trip"><span>${escapeHtml(bus.src)} <b>→</b> ${escapeHtml(bus.dest)}</span><small>${escapeHtml(bus.name)} · Seat ${state.selectedSeat} · ${new Date(`${state.travelDate}T12:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}</small></div>`;
  if (state.bookingStatus === 'processing' || state.bookingStatus === 'pending') return `<div class="modal-backdrop" role="presentation"><section class="payment-modal status-card" role="dialog" aria-modal="true"><div class="status-orbit"><i></i><b>${state.bookingStatus === 'processing' ? '₹' : '✓'}</b></div><div class="checkout-badge">${state.bookingStatus === 'processing' ? 'Payment processing' : 'Ticket pending'}</div><h2>${state.bookingStatus === 'processing' ? 'Verifying your demo payment' : 'Holding your favourite seat'}</h2><p class="modal-subtitle">${state.bookingStatus === 'processing' ? 'Please keep this screen open for a moment.' : 'We are confirming live seat availability with the operator.'}</p>${details}<div class="status-steps"><span class="done">Payment initiated</span><span class="${state.bookingStatus === 'pending' ? 'active' : ''}">Seat confirmation</span><span>Ticket issued</span></div></section></div>`;
  if (state.bookingStatus === 'confirmed' && state.ticket) return `<div class="modal-backdrop" role="presentation"><section class="payment-modal confirmation-card" role="dialog" aria-modal="true"><div class="success-check">✓</div><div class="checkout-badge">Ticket confirmed</div><h2>Your journey is booked!</h2><p class="modal-subtitle">A demo confirmation has been created. Have a wonderful trip.</p><div class="ticket"><div><span>Booking ID</span><strong>#${escapeHtml(state.ticket.id)}</strong></div><div><span>Passenger</span><strong>${escapeHtml(state.ticket.name)}</strong></div><div><span>Seat</span><strong>${state.ticket.seat}</strong></div><div><span>Paid</span><strong>${money.format(state.ticket.total)}</strong></div></div>${details}<button class="confirm-payment" id="close-confirmation">Done</button></section></div>`;
  if (state.bookingStatus === 'failed') return `<div class="modal-backdrop" role="presentation"><section class="payment-modal status-card" role="dialog" aria-modal="true"><div class="failed-mark">!</div><div class="checkout-badge">Action needed</div><h2>We could not confirm that seat.</h2><p class="modal-subtitle">${escapeHtml(state.ticket?.message || 'The seat may have just been selected by another traveller.')}</p>${details}<button class="confirm-payment" id="retry-payment">Choose another seat</button></section></div>`;
  return `<div class="modal-backdrop" role="presentation"><section class="payment-modal" role="dialog" aria-modal="true" aria-labelledby="payment-title"><button class="modal-close" id="close-payment" aria-label="Close checkout">×</button><div class="checkout-badge">Secure demo checkout</div><h2 id="payment-title">Almost there, ${escapeHtml(state.passengerName.split(' ')[0] || 'traveller')}.</h2><p class="modal-subtitle">This is a practice payment screen. No money will be charged.</p>${details}<div class="payment-methods"><button class="payment-method ${state.paymentMethod === 'upi' ? 'selected' : ''}" data-method="upi"><b>UPI</b><span>Google Pay, PhonePe & more</span></button><button class="payment-method ${state.paymentMethod === 'card' ? 'selected' : ''}" data-method="card"><b>Card</b><span>Credit or debit card</span></button></div><div class="demo-payment"><span>${state.paymentMethod === 'upi' ? 'UPI ID' : 'Card number'}</span><strong>${state.paymentMethod === 'upi' ? 'traveller@triply' : '•••• 4242'}</strong><small>Demo details only</small></div><div class="payment-total"><span>Total payable</span><strong>${money.format(total)}</strong></div><button class="confirm-payment" id="confirm-payment">Pay ${money.format(total)} securely <b>→</b></button><p class="security-note">🔒 Demo mode — this creates a reservation only.</p></section></div>`;
}

function render() {
  const bus = activeBus();
  const services = matchingBuses();
  const cities = [...new Set(state.buses.flatMap((service) => [service.src, service.dest]))].sort();
  const cityOptions = cities.map((city) => `<option value="${escapeHtml(city)}"></option>`).join('');
  document.querySelector('#app').innerHTML = `<header class="topbar"><a class="brand" href="#top"><b>T</b>triply</a><nav><a class="active" href="#search">Bus tickets</a><a href="#how-it-works">How it works</a><a href="/driver.html">Driver portal</a><a href="/admin.html">Agency portal</a><a href="#support">Help</a></nav><div class="status ${state.connected ? 'online' : ''}"><i></i>${state.connected ? `Live updates on${state.lastSyncedAt ? ` · synced ${state.lastSyncedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : ''}` : 'Preview mode'}</div></header>
  <main id="top"><section class="hero" id="search"><div class="hero-copy"><p class="eyebrow">Simple, reliable bus travel</p><h1>Go somewhere<br><em>worth waking up for.</em></h1><p>Compare comfortable buses, choose your exact seat, and book in a few moments.</p></div>
  <form class="search-card" id="search-form">${renderCityPicker('from', 'From', state.searchFrom, cities)}<button class="swap" type="button" aria-label="Swap route">⇄</button>${renderCityPicker('to', 'To', state.searchTo, cities)}<div class="search-field"><label for="date">Travel date</label><input id="date" type="date" min="${tomorrow}" value="${state.travelDate}"></div><div class="search-field"><label for="travellers">Passengers</label><select id="travellers"><option>1 passenger</option><option>2 passengers</option><option>3 passengers</option></select></div><button class="search-button">Find buses <b>→</b></button></form><div class="trust"><span>✓ Free seat selection</span><span>✓ Instant confirmation</span><span>✓ Secure checkout</span><span>↗ Live GPS where enabled</span></div></section>
  <section class="results ${state.searched ? 'visible' : ''}" id="results"><div class="section-heading"><div><p class="eyebrow">Available journeys</p><h2>${escapeHtml(state.searchFrom)} <b>→</b> ${escapeHtml(state.searchTo)}</h2><p>${new Date(`${state.travelDate}T12:00:00`).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })} · ${services.length} service${services.length === 1 ? '' : 's'} found</p></div><button id="modify-search">Modify search</button></div>${services.length ? `<div class="journey-layout"><div class="service-list">${services.map(renderService).join('')}</div><aside class="booking-panel">${renderPanel()}</aside></div>` : `<div class="empty-results"><strong>No direct service found for this route.</strong><span>Try one of the listed city pairs or change your journey date.</span></div>`}</section>
  <section class="how" id="how-it-works"><p class="eyebrow">Built for a smooth journey</p><h2>Book the seat you actually want.</h2><div class="features"><article><b>01</b><h3>Find your ride</h3><p>Search reliable services on the route you need.</p></article><article><b>02</b><h3>Pick your seat</h3><p>See live availability and select your preferred spot.</p></article><article><b>03</b><h3>Travel easy</h3><p>Receive your booking confirmation instantly.</p></article></div></section></main><footer id="support"><span>triply · Made for unhurried journeys</span><span>Need help? support@triply.example</span></footer>${renderPaymentModal()}`;
  bindEvents();
}

function bindEvents() {
  document.querySelector('#search-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    state.cityPicker = ''; state.cityFilter = '';
    state.travelDate = document.querySelector('#date').value;
    const services = matchingBuses();
    if (services.length) { state.selectedBusNo = services[0].busNo; state.selectedSeat = null; await refreshAvailability(); }
    state.searched = true;
    render();
    document.querySelector('#results').scrollIntoView({ behavior: 'smooth' });
  });
  document.querySelector('.swap').addEventListener('click', () => { [state.searchFrom, state.searchTo] = [state.searchTo, state.searchFrom]; render(); });
  document.querySelectorAll('.city-trigger').forEach((button) => button.addEventListener('click', () => { state.cityPicker = state.cityPicker === button.dataset.picker ? '' : button.dataset.picker; state.cityFilter = ''; render(); if (state.cityPicker) setTimeout(() => document.querySelector('.city-filter')?.focus(), 0); }));
  const cityFilter = document.querySelector('.city-filter');
  if (cityFilter) cityFilter.addEventListener('input', (event) => { state.cityFilter = event.target.value; render(); setTimeout(() => document.querySelector('.city-filter')?.focus(), 0); });
  document.querySelectorAll('.city-options button').forEach((button) => button.addEventListener('click', () => { if (button.dataset.field === 'from') state.searchFrom = button.dataset.city; else state.searchTo = button.dataset.city; state.cityPicker = ''; state.cityFilter = ''; render(); }));
  document.onclick = (event) => {
    if (state.cityPicker && !event.target.closest('.city-picker')) { state.cityPicker = ''; state.cityFilter = ''; render(); }
  };
  document.onkeydown = (event) => {
    if (event.key === 'Escape' && state.cityPicker) { state.cityPicker = ''; state.cityFilter = ''; render(); }
  };
  document.querySelector('#modify-search').addEventListener('click', () => document.querySelector('#search').scrollIntoView({ behavior: 'smooth' }));
  document.querySelectorAll('.service-card').forEach((card) => card.addEventListener('click', async () => { state.selectedBusNo = Number(card.dataset.bus); state.selectedSeat = null; await refreshAvailability(); await refreshTracking(); render(); }));
  const refreshTrack = document.querySelector('#refresh-track');
  if (refreshTrack) refreshTrack.addEventListener('click', async () => { refreshTrack.textContent = 'Checking…'; await refreshTracking(); render(); });
  document.querySelectorAll('.seat:not(.booked)').forEach((seat) => seat.addEventListener('click', () => { state.selectedSeat = Number(seat.dataset.seat); render(); }));
  const passenger = document.querySelector('#passenger');
  const checkout = document.querySelector('#checkout-form');
  if (passenger) passenger.addEventListener('input', (event) => { state.passengerName = event.target.value; });
  if (checkout) checkout.addEventListener('submit', (event) => {
    event.preventDefault();
    state.passengerName = passenger.value.trim();
    if (!state.passengerName || !state.selectedSeat) return;
    state.paymentOpen = true;
    state.bookingStatus = 'payment';
    render();
  });
  const applyCoupon = document.querySelector('#apply-coupon');
  if (applyCoupon) applyCoupon.addEventListener('click', () => {
    const code = document.querySelector('#coupon').value.trim().toUpperCase();
    state.coupon = coupons[code] ? code : '';
    state.couponMessage = coupons[code] ? `Applied ${code}: ${coupons[code].label}.` : (code ? 'That coupon is not available. Try WELCOME10.' : 'Enter a coupon code to apply it.');
    render();
  });
  const closePayment = document.querySelector('#close-payment');
  if (closePayment) closePayment.addEventListener('click', () => { state.paymentOpen = false; render(); });
  document.querySelectorAll('.payment-method').forEach((button) => button.addEventListener('click', () => { state.paymentMethod = button.dataset.method; render(); }));
  const confirmPayment = document.querySelector('#confirm-payment');
  if (confirmPayment) confirmPayment.addEventListener('click', bookSeat);
  const closeConfirmation = document.querySelector('#close-confirmation');
  if (closeConfirmation) closeConfirmation.addEventListener('click', () => { state.paymentOpen = false; state.bookingStatus = 'payment'; state.ticket = null; render(); });
  const retryPayment = document.querySelector('#retry-payment');
  if (retryPayment) retryPayment.addEventListener('click', () => { state.paymentOpen = false; state.bookingStatus = 'payment'; state.ticket = null; state.selectedSeat = null; render(); });
}

async function bookSeat(event) {
  event.preventDefault(); const message = document.querySelector('#booking-message'); const name = state.passengerName.trim(); if (!name || !state.selectedSeat) return;
  state.bookingStatus = 'processing'; render(); await wait(850); state.bookingStatus = 'pending'; render(); await wait(650);
  try {
    const response = await fetch(`${API_BASE}/book`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ busNumber: state.selectedBusNo, seatNumber: state.selectedSeat, passengerName: name }) });
    const data = await response.json(); if (!response.ok || !data.success) throw new Error(data.message || 'Booking could not be completed.');
    state.ticket = { id: data.bookingId, seat: data.seatNo, name, total: bookingTotal(activeBus().fare) }; state.coupon = ''; state.couponMessage = ''; state.passengerName = ''; await refreshAvailability(); state.bookingStatus = 'confirmed'; render();
  } catch (error) { state.ticket = { message: error.message || 'Unable to reach the booking server.' }; state.bookingStatus = 'failed'; render(); }
}

await loadBuses();
render();

// Keep every open client aligned with the booking API. State is preserved before
// rendering so a passenger can keep typing while other seats change remotely.
setInterval(async () => {
  if (!state.searched) return;
  const before = JSON.stringify(state.seats);
  const previousTracking = JSON.stringify(state.tracking);
  const wasConnected = state.connected;
  await refreshAvailability();
  await refreshTracking();
  if (before !== JSON.stringify(state.seats) || previousTracking !== JSON.stringify(state.tracking) || wasConnected !== state.connected) render();
}, 3000);
