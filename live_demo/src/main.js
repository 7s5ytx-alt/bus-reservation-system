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
  ], selectedBusNo: 101, selectedSeat: null, seats: [], connected: false, searched: false, travelDate: tomorrow, passengerName: '', lastSyncedAt: null, searchFrom: 'Bangalore', searchTo: 'Chennai'
};
const escapeHtml = (value) => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
const activeBus = () => state.buses.find((bus) => bus.busNo === state.selectedBusNo) ?? state.buses[0];
const matchingBuses = () => state.buses.filter((bus) =>
  bus.src.toLowerCase() === state.searchFrom.trim().toLowerCase()
  && bus.dest.toLowerCase() === state.searchTo.trim().toLowerCase()
);
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
    state.buses = (await response.json()).map((bus) => ({ ...bus, ...(tripDetails[bus.busNo] ?? { departure: '21:00', arrival: '06:00', duration: '8h', fare: 999, type: 'AC Sleeper', rating: '4.3' }) }));
  } catch { /* Preview mode uses local sample data. */ }
  await refreshAvailability();
}

const availableSeats = () => state.seats.filter((seat) => !seat.isBooked).length;

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
  return `<div class="panel-heading"><div><p class="eyebrow">Choose your seat</p><h2>${escapeHtml(bus.name)}</h2></div><span>${escapeHtml(bus.type)}</span></div>
    <div class="legend"><i></i> Available <i class="key-selected"></i> Selected <i class="key-booked"></i> Booked</div>
    <div class="bus-shell"><div class="wheel">◔</div><div class="seat-grid">${renderSeatMap()}</div></div>
    <form id="checkout-form" class="checkout"><label for="passenger">Passenger details</label><input id="passenger" placeholder="Full name as on ID" maxlength="100" value="${escapeHtml(state.passengerName)}" required ${selected ? '' : 'disabled'}>
      <div class="fare-row"><span>${selected ? `Seat ${selected.seatNo}` : 'Select a seat'}</span><strong>${selected ? money.format(bus.fare) : '—'}</strong></div>
      <button type="submit" ${selected ? '' : 'disabled'}>Continue to payment <b>→</b></button><p id="booking-message">${state.connected ? 'Your selected seat is held while you complete booking.' : 'Start the Java server to make a live reservation.'}</p></form>`;
}

function render() {
  const bus = activeBus();
  const services = matchingBuses();
  const cities = [...new Set(state.buses.flatMap((service) => [service.src, service.dest]))].sort();
  const cityOptions = cities.map((city) => `<option value="${escapeHtml(city)}"></option>`).join('');
  document.querySelector('#app').innerHTML = `<header class="topbar"><a class="brand" href="#top"><b>T</b>triply</a><nav><a class="active" href="#search">Bus tickets</a><a href="#how-it-works">How it works</a><a href="#support">Help</a></nav><div class="status ${state.connected ? 'online' : ''}"><i></i>${state.connected ? `Live updates on${state.lastSyncedAt ? ` · synced ${state.lastSyncedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : ''}` : 'Preview mode'}</div></header>
  <main id="top"><section class="hero" id="search"><div class="hero-copy"><p class="eyebrow">Simple, reliable bus travel</p><h1>Go somewhere<br><em>worth waking up for.</em></h1><p>Compare comfortable buses, choose your exact seat, and book in a few moments.</p></div>
  <form class="search-card" id="search-form"><div class="search-field"><label for="from">From</label><input id="from" list="cities" value="${escapeHtml(state.searchFrom)}"><small>Departure city</small></div><button class="swap" type="button" aria-label="Swap route">⇄</button><div class="search-field"><label for="to">To</label><input id="to" list="cities" value="${escapeHtml(state.searchTo)}"><small>Arrival city</small></div><datalist id="cities">${cityOptions}</datalist><div class="search-field"><label for="date">Travel date</label><input id="date" type="date" min="${tomorrow}" value="${state.travelDate}"></div><div class="search-field"><label for="travellers">Passengers</label><select id="travellers"><option>1 passenger</option><option>2 passengers</option><option>3 passengers</option></select></div><button class="search-button">Find buses <b>→</b></button></form><div class="trust"><span>✓ Free seat selection</span><span>✓ Instant confirmation</span><span>✓ Secure checkout</span></div></section>
  <section class="results ${state.searched ? 'visible' : ''}" id="results"><div class="section-heading"><div><p class="eyebrow">Available journeys</p><h2>${escapeHtml(state.searchFrom)} <b>→</b> ${escapeHtml(state.searchTo)}</h2><p>${new Date(`${state.travelDate}T12:00:00`).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })} · ${services.length} service${services.length === 1 ? '' : 's'} found</p></div><button id="modify-search">Modify search</button></div>${services.length ? `<div class="journey-layout"><div class="service-list">${services.map(renderService).join('')}</div><aside class="booking-panel">${renderPanel()}</aside></div>` : `<div class="empty-results"><strong>No direct service found for this route.</strong><span>Try one of the listed city pairs or change your journey date.</span></div>`}</section>
  <section class="how" id="how-it-works"><p class="eyebrow">Built for a smooth journey</p><h2>Book the seat you actually want.</h2><div class="features"><article><b>01</b><h3>Find your ride</h3><p>Search reliable services on the route you need.</p></article><article><b>02</b><h3>Pick your seat</h3><p>See live availability and select your preferred spot.</p></article><article><b>03</b><h3>Travel easy</h3><p>Receive your booking confirmation instantly.</p></article></div></section></main><footer id="support"><span>triply · Made for unhurried journeys</span><span>Need help? support@triply.example</span></footer>`;
  bindEvents();
}

function bindEvents() {
  document.querySelector('#search-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    state.searchFrom = document.querySelector('#from').value.trim();
    state.searchTo = document.querySelector('#to').value.trim();
    state.travelDate = document.querySelector('#date').value;
    const services = matchingBuses();
    if (services.length) { state.selectedBusNo = services[0].busNo; state.selectedSeat = null; await refreshAvailability(); }
    state.searched = true;
    render();
    document.querySelector('#results').scrollIntoView({ behavior: 'smooth' });
  });
  document.querySelector('.swap').addEventListener('click', () => { const from = document.querySelector('#from'); const to = document.querySelector('#to'); [from.value, to.value] = [to.value, from.value]; });
  document.querySelector('#modify-search').addEventListener('click', () => document.querySelector('#search').scrollIntoView({ behavior: 'smooth' }));
  document.querySelectorAll('.service-card').forEach((card) => card.addEventListener('click', async () => { state.selectedBusNo = Number(card.dataset.bus); state.selectedSeat = null; await refreshAvailability(); render(); }));
  document.querySelectorAll('.seat:not(.booked)').forEach((seat) => seat.addEventListener('click', () => { state.selectedSeat = Number(seat.dataset.seat); render(); }));
  const passenger = document.querySelector('#passenger');
  const checkout = document.querySelector('#checkout-form');
  if (passenger) passenger.addEventListener('input', (event) => { state.passengerName = event.target.value; });
  if (checkout) checkout.addEventListener('submit', bookSeat);
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
