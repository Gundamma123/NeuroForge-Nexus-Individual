// // import React from "react";
// // import ReactDOM from "react-dom/client";
// // import App from "./App.jsx";
// // import "./index.css";

// // ReactDOM.createRoot(document.getElementById("root")).render(
// //   <React.StrictMode>
// //     <App />
// //   </React.StrictMode>
// // );

// import React from "react";
// import ReactDOM from "react-dom/client";
// import App from "./App.jsx";
// import { ThemeProvider } from "./context/ThemeContext.jsx";
// import "./index.css";
// import "./theme-able.css"; // must stay AFTER index.css

// document.documentElement.setAttribute("data-theme", "light");

// ReactDOM.createRoot(document.getElementById("root")).render(
//   <React.StrictMode>
//     <ThemeProvider>
//       <App />
//     </ThemeProvider>
//   </React.StrictMode>
// );
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.jsx";
// import { ThemeProvider } from "./context/ThemeContext.jsx";
import { ThemeProvider } from "./ThemeContext.jsx";
import "./index.css";
 // must stay AFTER index.css

// The theme attribute is set by the small script in index.html (before first
// paint) and kept in sync by ThemeProvider. Do not force it here.
ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ThemeProvider>
      <App />
    </ThemeProvider>
  </React.StrictMode>
);