import "../src/styles.css";

export const metadata = {
  title: "Pilosia",
  description: "Sistema de información académica",
};

export default function RootLayout({ children }) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
