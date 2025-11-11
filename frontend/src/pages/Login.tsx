import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import api from "@/Services/api";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Lock, User, Award } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { jwtDecode } from "jwt-decode";

type JwtPayload = {
  sub: string;
  exp: number;
  role?: string;
  userId?: number;
  email?: string;
};

export default function Login() {
  const [formData, setFormData] = useState({ username: "", password: "" });
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  const { toast } = useToast();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      const response = await api.post("/api/auth/login", formData, {
        withCredentials: true,
      });

      const { token, message } = response.data;
      const decoded = jwtDecode<JwtPayload>(token);
      const userId = decoded.userId || response.data.userid || null;

      sessionStorage.setItem("authToken", token);
      if (userId) {
        sessionStorage.setItem("userid", String(userId));
      }

      toast({
        title: "Login successful",
        description: message || "Welcome!",
      });

      if (decoded.role === "ADMIN") {
        navigate("/dashboard");
      } else {
        navigate("/dashboard");
      }
    } catch (error: any) {
      toast({
        title: "Login failed",
        description:
          error.response?.data?.message ||
          error.message ||
          "Something went wrong",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-admin-bg via-primary/5 to-admin-accent-light flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-gradient-to-r from-primary to-primary-glow rounded-full mb-4 shadow-lg">
            <Award className="h-8 w-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold bg-gradient-to-r from-primary to-primary-glow bg-clip-text text-transparent">
            GenKart Admin
          </h1>
          <p className="text-muted-foreground mt-2">
            Smart Certificate Management System
          </p>
        </div>

        <Card className="shadow-xl border-0 bg-white/95 backdrop-blur">
          <CardHeader className="space-y-1">
            <CardTitle className="text-1xl font-bold text-center">
              👋 Welcome Back, Administrator
            </CardTitle>
            <CardDescription className="text-center">
              Enter your credentials to access the admin panel
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username">Username</Label>
                <div className="relative">
                  <User className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="username"
                    type="text"
                    placeholder="Enter your username"
                    value={formData.username}
                    onChange={(e) =>
                      setFormData({ ...formData, username: e.target.value })
                    }
                    className="pl-9"
                    required
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <div className="relative">
                  <Lock className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="password"
                    type="password"
                    placeholder="Enter your password"
                    value={formData.password}
                    onChange={(e) =>
                      setFormData({ ...formData, password: e.target.value })
                    }
                    className="pl-9"
                    required
                  />
                </div>
              </div>

              <Button
                type="submit"
                className="w-full bg-gradient-to-r from-primary to-primary-glow hover:shadow-lg transition-all duration-200"
                disabled={isLoading}
              >
                {isLoading ? "Signing in..." : "Sign in"}
              </Button>
            </form>
          </CardContent>
        </Card>

        <p className="text-center text-sm text-muted-foreground mt-6">
          Powered with trust by Urbanites Technologies Pvt. Ltd.
        </p>
      </div>
    </div>
  );
}
