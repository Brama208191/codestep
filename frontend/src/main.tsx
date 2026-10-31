import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import ProblemList from "./pages/ProblemList";
import ProblemPage from "./pages/ProblemPage";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ProblemList />} />
        <Route path="/problems/:slug" element={<ProblemPage />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
