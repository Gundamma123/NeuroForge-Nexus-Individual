export default function ComingSoon({ title = "Coming Soon" }) {
  return (
    <div style={{ padding: "32px" }}>
      <h1>{title}</h1>
      <p>This feature is currently under development.</p>
    </div>
  );
}
