import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
} from "react-router-dom";

import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Certificates from "./pages/Certificates";
import Reports from "./pages/Reports";
import Profile from "./pages/Profile";
import Registation from "./pages/registation";
import UploadTemplate from "./pages/upload-template";
import { AdminLayout } from "./components/AdminLayout";
import NotFound from "./pages/NotFound";

const queryClient = new QueryClient();

// Authenticated route wrapper
const PrivateRoute = () => {
  const token = sessionStorage.getItem("authToken");
  return token ? <Outlet /> : <Navigate to="/login" replace />;
};

//  Helper function to check admin
const isUserAdmin = (): boolean => {
  const token = sessionStorage.getItem("authToken");
  if (!token) return false;

  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.role?.toUpperCase() === "ADMIN";
  } catch (e) {
    console.error("Token decode failed:", e);
    return false;
  }
};

const App = () => {
  const isAdmin = isUserAdmin();

  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter>
          <Routes>
            {/* Root redirect */}
            <Route
              path="/"
              element={
                sessionStorage.getItem("authToken") ? (
                  <Navigate to="/dashboard" replace />
                ) : (
                  <Navigate to="/login" replace />
                )
              }
            />

            {/* Public Route */}
            <Route path="/login" element={<Login />} />

            {/* Protected Routes */}
            <Route element={<PrivateRoute />}>
              <Route path="/" element={<AdminLayout />}>
                <Route path="dashboard" element={<Dashboard />} />
                <Route path="certificates" element={<Certificates />} />
                {/*  Pass isAdmin prop here */}
                <Route path="reports" element={<Reports isAdmin={isAdmin} />} />
                <Route path="profile" element={<Profile />} />
                <Route path="upload-template" element={<UploadTemplate />} />
                <Route path="registation" element={<Registation />} />
              </Route>
            </Route>

            {/* 404 Not Found */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </TooltipProvider>
    </QueryClientProvider>
  );
};

export default App;
