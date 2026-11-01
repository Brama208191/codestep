import { StrictMode, Suspense, lazy } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import ProblemList from "./pages/ProblemList";
import "./styles.css";

// The problem page carries the Monaco editor (most of the app's size), so load it only when needed.
const ProblemPage = lazy(() => import("./pages/ProblemPage"));

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ProblemList />} />
        <Route
          path="/problems/:slug"
          element={
            <Suspense fallback={null}>
              <ProblemPage />
            </Suspense>
          }
        />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
