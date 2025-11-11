import { useState, useEffect } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import {
  BarChart3,
  FileText,
  Users,
  LogOut,
  Menu,
  Award,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

function decodeToken(token: string) {
  try {
    const base64Url = token.split(".")[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join("")
    );
    return JSON.parse(jsonPayload);
  } catch (error) {
    console.error("Invalid token:", error);
    return null;
  }
}

export function AdminSidebar() {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [role, setRole] = useState<string | null>(null);
  const location = useLocation();
  const navigate = useNavigate();

  
  useEffect(() => {
    const token = sessionStorage.getItem("authToken");
    if (token) {
      const decoded = decodeToken(token);
      if (decoded && decoded.role) {
        setRole(decoded.role.toUpperCase());
      }
    }
  }, []);


  const navigation = [
    { name: "Dashboard", href: "/dashboard", icon: BarChart3 },
    { name: "Certificates", href: "/certificates", icon: Award },
    { name: "Reports", href: "/reports", icon: FileText },
    { name: "Profile", href: "/profile", icon: Users },
    {
      name: "Register User",
      href: "/registation",
      icon: Users,
      role: "ADMIN", 
    },
  ];

  
  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth < 1024) {
        setIsCollapsed(true);
      } else {
        setIsCollapsed(false);
      }
    };
    handleResize();
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

 
  const handleLogout = () => {
    sessionStorage.removeItem("authToken");
    navigate("/login");
  };

  return (
    <div
      className={cn(
        "bg-admin-sidebar text-white transition-all duration-300 flex flex-col",
        isCollapsed ? "w-16" : "w-64"
      )}
    >
    
      <div className="p-4 border-b border-admin-sidebar-muted">
        <div className="flex items-center justify-between">
          {!isCollapsed && (
            <h1 className="text-xl font-bold bg-gradient-to-r from-primary to-primary-glow bg-clip-text text-transparent">
              GenKart Admin
            </h1>
          )}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setIsCollapsed(!isCollapsed)}
            className="text-white hover:bg-admin-sidebar-muted"
          >
            <Menu className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <nav className="flex-1 p-4 space-y-2">
        {navigation
          .filter((item) => !item.role || item.role === role) 
          .map((item) => {
            const isActive = location.pathname === item.href;
            return (
              <NavLink
                key={item.name}
                to={item.href}
                className={cn(
                  "flex items-center space-x-3 px-3 py-2 rounded-lg transition-all duration-200 group",
                  isActive
                    ? "bg-primary text-white shadow-lg"
                    : "text-gray-300 hover:bg-admin-sidebar-muted hover:text-white"
                )}
              >
                <item.icon
                  className={cn(
                    "h-5 w-5 transition-transform duration-200",
                    isActive && "scale-110"
                  )}
                />
                {!isCollapsed && <span className="font-medium">{item.name}</span>}
              </NavLink>
            );
          })}
      </nav>

      <div className="p-4 border-t border-admin-sidebar-muted">
        <Button
          onClick={handleLogout}
          variant="ghost"
          className={cn(
            "w-full justify-start text-gray-300 hover:bg-red-600 hover:text-white transition-colors",
            isCollapsed && "justify-center"
          )}
        >
          <LogOut className="h-5 w-5" />
          {!isCollapsed && <span className="ml-3">Logout</span>}
        </Button>
      </div>
    </div>
  );
}
