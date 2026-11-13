import './style.css';

const API_BASE = 'http://localhost:8080/api';
const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
const state = {
  buses: [
    { busNo: 101, name: 'Kaveri Express', src: 'Bangalore', dest: 'Chennai', totalSeats: 24, departure: '21:30', arrival: '05:45', duration: '8h 15m', fare: 899, type: 'AC Sleeper' },
    { busNo: 102, name: 'Royal Volvo', src: 'Hyderabad', dest: 'Bangalore', totalSeats: 28, departure: '22:00', arrival: '06:30', duration: '8h 30m', fare: 1199, type: 'Volvo Multi-Axle' },
    { busNo: 103, name: 'Night Rider', src: 'Mumbai', dest: 'Goa', totalSeats: 20, departure: '20:45', arrival: '07:15', duration: '10h 30m', fare: 1049, type: 'AC Sleeper' }
  ], selectedBusNo: 101, selectedSeat: null, seats: [], connected: false, searched: false, travelDate: tomorrow, passengerName: '', lastSyncedAt: null
};
const escapeHtml = (value) => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
const activeBus = () => state.buses.find((bus) => bus.busNo === state.selectedBusNo) ?? state.buses[0];
const fallbackSeats = (bus) => Array.from({ length: bus.totalSeats }, (_, index) => ({ seatNo: index + 1, isBooked: false, passenger: '' }));

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

async function loadBuses() {
  try {
    const response = await fetch(`${API_BASE}/buses`);
    if (!response.ok) throw new Error();
    const details = [['21:30', '05:45', '8h 15m', 899, 'AC Sleeper'], ['22:00', '06:30', '8h 30m', 1199, 'Volvo Multi-Axle'], ['20:45', '07:15', '10h 30m', 1049, 'AC Sleeper']];
    state.buses = (await response.json()).map((bus, index) => ({ ...bus, departure: details[index]?.[0] ?? '21:00', arrival: details[index]?.[1] ?? '06:00', duration: details[index]?.[2] ?? '8h', fare: details[index]?.[3] ?? 999, type: details[index]?.[4] ?? 'AC Sleeper' }));
  } catch { /* Preview mode uses local sample data. */ }
  await refreshAvailability();
}

const availableSeats = () => state.seats.filter((seat) => !seat.isBooked).length;

function renderService(bus) {
  const chosen = bus.busNo === state.selectedBusNo;
  const available = chosen ? availableSeats() : (bus.availableSeats ?? bus.totalSeats);
  return `<article class="service-card ${chosen ? 'selected' : ''}" data-bus="${bus.busNo}">
    <div class="service-main"><div class="operator"><div class="operator-logo">${escapeHtml(bus.name[0])}</div><div><h3>${escapeHtml(bus.name)}</h3><p>${escapeHtml(bus.type)} · 4.7 <b>★</b></p></div></div>
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
  return `<div class="panel-heading"><div><p class="eyebrow">Choose your seat</p><h2>${escapeHtml(bus.name)}</h2></div><span>${escapeHtml(bus.type)}</span></div>
    <div class="legend"><i></i> Available <i class="key-selected"></i> Selected <i class="key-booked"></i> Booked</div>
    <div class="bus-shell"><div class="wheel">◔</div><div class="seat-grid">${renderSeatMap()}</div></div>
    <form id="checkout-form" class="checkout"><label for="passenger">Passenger details</label><input id="passenger" placeholder="Full name as on ID" maxlength="100" value="${escapeHtml(state.passengerName)}" required ${selected ? '' : 'disabled'}>
      <div class="fare-row"><span>${selected ? `Seat ${selected.seatNo}` : 'Select a seat'}</span><strong>${selected ? money.format(bus.fare) : '—'}</strong></div>
      <button type="submit" ${selected ? '' : 'disabled'}>Continue to payment <b>→</b></button><p id="booking-message">${state.connected ? 'Your selected seat is held while you complete booking.' : 'Start the Java server to make a live reservation.'}</p></form>`;
}

function render() {
  const bus = activeBus();
  document.querySelector('#app').innerHTML = `<header class="topbar"><a class="brand" href="#top"><b>T</b>triply</a><nav><a class="active" href="#search">Bus tickets</a><a href="#how-it-works">How it works</a><a href="#support">Help</a></nav><div class="status ${state.connected ? 'online' : ''}"><i></i>${state.connected ? `Live updates on${state.lastSyncedAt ? ` · synced ${state.lastSyncedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : ''}` : 'Preview mode'}</div></header>
  <main id="top"><section class="hero" id="search"><div class="hero-copy"><p class="eyebrow">Simple, reliable bus travel</p><h1>Go somewhere<br><em>worth waking up for.</em></h1><p>Compare comfortable buses, choose your exact seat, and book in a few moments.</p></div>
  <form class="search-card" id="search-form"><div class="search-field"><label for="from">From</label><input id="from" value="${escapeHtml(bus.src)}"><small>Departure city</small></div><button class="swap" type="button" aria-label="Swap route">⇄</button><div class="search-field"><label for="to">To</label><input id="to" value="${escapeHtml(bus.dest)}"><small>Arrival city</small></div><div class="search-field"><label for="date">Travel date</label><input id="date" type="date" min="${tomorrow}" value="${state.travelDate}"></div><div class="search-field"><label for="travellers">Passengers</label><select id="travellers"><option>1 passenger</option><option>2 passengers</option><option>3 passengers</option></select></div><button class="search-button">Find buses <b>→</b></button></form><div class="trust"><span>✓ Free seat selection</span><span>✓ Instant confirmation</span><span>✓ Secure checkout</span></div></section>
  <section class="results ${state.searched ? 'visible' : ''}" id="results"><div class="section-heading"><div><p class="eyebrow">Available journeys</p><h2>${escapeHtml(bus.src)} <b>→</b> ${escapeHtml(bus.dest)}</h2><p>${new Date(`${state.travelDate}T12:00:00`).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })} · ${state.buses.length} services found</p></div><button id="modify-search">Modify search</button></div><div class="journey-layout"><div class="service-list">${state.buses.map(renderService).join('')}</div><aside class="booking-panel">${renderPanel()}</aside></div></section>
  <section class="how" id="how-it-works"><p class="eyebrow">Built for a smooth journey</p><h2>Book the seat you actually want.</h2><div class="features"><article><b>01</b><h3>Find your ride</h3><p>Search reliable services on the route you need.</p></article><article><b>02</b><h3>Pick your seat</h3><p>See live availability and select your preferred spot.</p></article><article><b>03</b><h3>Travel easy</h3><p>Receive your booking confirmation instantly.</p></article></div></section></main><footer id="support"><span>triply · Made for unhurried journeys</span><span>Need help? support@triply.example</span></footer>`;
  bindEvents();
}

function bindEvents() {
  document.querySelector('#search-form').addEventListener('submit', async (event) => { event.preventDefault(); state.travelDate = document.querySelector('#date').value; state.searched = true; await refreshAvailability(); render(); document.querySelector('#results').scrollIntoView({ behavior: 'smooth' }); });
  document.querySelector('.swap').addEventListener('click', () => { const from = document.querySelector('#from'); const to = document.querySelector('#to'); [from.value, to.value] = [to.value, from.value]; });
  document.querySelector('#modify-search').addEventListener('click', () => document.querySelector('#search').scrollIntoView({ behavior: 'smooth' }));
  document.querySelectorAll('.service-card').forEach((card) => card.addEventListener('click', async () => { state.selectedBusNo = Number(card.dataset.bus); state.selectedSeat = null; await refreshAvailability(); render(); }));
  document.querySelectorAll('.seat:not(.booked)').forEach((seat) => seat.addEventListener('click', () => { state.selectedSeat = Number(seat.dataset.seat); render(); }));
  document.querySelector('#passenger').addEventListener('input', (event) => { state.passengerName = event.target.value; });
  document.querySelector('#checkout-form').addEventListener('submit', bookSeat);
}

async function bookSeat(event) {
  event.preventDefault(); const form = event.currentTarget; const message = document.querySelector('#booking-message'); const name = form.querySelector('#passenger').value.trim(); state.passengerName = name; if (!name || !state.selectedSeat) return;
  const button = form.querySelector('button'); button.disabled = true; button.textContent = 'Confirming your seat…';
  try {
    const response = await fetch(`${API_BASE}/book`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ busNumber: state.selectedBusNo, seatNumber: state.selectedSeat, passengerName: name }) });
    const data = await response.json(); if (!response.ok || !data.success) throw new Error(data.message || 'Booking could not be completed.');
    message.className = 'success'; message.textContent = `Confirmed! Booking #${data.bookingId} · Seat ${data.seatNo} is reserved for ${name}.`; state.passengerName = ''; state.selectedSeat = null; await refreshAvailability(); setTimeout(render, 1100);
  } catch (error) { message.className = 'error'; message.textContent = error.message || 'Unable to reach the booking server.'; button.disabled = false; button.innerHTML = 'Continue to payment <b>→</b>'; }
}

await loadBuses();
render();

// Keep every open client aligned with the booking API. State is preserved before
// rendering so a passenger can keep typing while other seats change remotely.
setInterval(async () => {
  if (!state.searched) return;
  const before = JSON.stringify(state.seats);
  const wasConnected = state.connected;
  await refreshAvailability();
  if (before !== JSON.stringify(state.seats) || wasConnected !== state.connected) render();
}, 3000);
