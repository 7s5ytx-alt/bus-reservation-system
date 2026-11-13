import { defineConfig } from 'vite';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  build: {
    rollupOptions: {
      input: {
        home: fileURLToPath(new URL('./index.html', import.meta.url)),
        booking: fileURLToPath(new URL('./booking.html', import.meta.url)),
        bookings: fileURLToPath(new URL('./bookings.html', import.meta.url)),
        policies: fileURLToPath(new URL('./policies.html', import.meta.url))
      }
    }
  }
});
