/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef4fc',
          100: '#d7e3f8',
          200: '#b0c8f1',
          500: '#2f69b8',
          600: '#1b4b8f',
          700: '#153b73',
          900: '#0b254a',
        },
      },
    },
  },
  plugins: [],
};
