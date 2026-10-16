// src/context/ThemeContext.jsx
// If your existing file already (1) sets data-theme on <html>, (2) does it in a
// layout effect, and (3) reads localStorage "nf_theme" on first render, keep yours
// and only change its default to "light". Keep your own export names if they differ.
import { createContext, useCallback, useContext, useLayoutEffect, useState } from "react";

const STORAGE_KEY = "nf_theme";

const readTheme = () => {
  try {
    return localStorage.getItem(STORAGE_KEY) === "dark" ? "dark" : "light";
  } catch {
    return "light";
  }
};

const ThemeContext = createContext({ theme: "light", toggleTheme: () => {} });

export const ThemeProvider = ({ children }) => {
  const [theme, setTheme] = useState(readTheme);

  useLayoutEffect(() => {
    const root = document.documentElement;
    root.setAttribute("data-theme", theme);
    root.style.colorScheme = theme;
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      /* storage can be blocked; the theme still applies for this session */
    }
  }, [theme]);

  const toggleTheme = useCallback(
    () => setTheme((t) => (t === "light" ? "dark" : "light")),
    []
  );

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => useContext(ThemeContext);
export default ThemeContext;