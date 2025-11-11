"use client";

import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "@/Services/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/components/ui/use-toast";
import { User, ShieldCheck, Lock, Edit, BarChart3 } from "lucide-react";

interface ProfileData {
  name: string;
  username: string;
  email: string;
  role: string;
}

interface StatsData {
  totalCertificates: number;
  lastLogin: string;
}

const Profile: React.FC = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("profile");
  const [profileData, setProfileData] = useState<ProfileData>({
    name: "",
    username: "",
    email: "",
    role: "",
  });
  const [stats, setStats] = useState<StatsData | null>(null);
  const [passwordData, setPasswordData] = useState({ currentPassword: "", newPassword: "" });
  const [loading, setLoading] = useState(false);
  const { toast } = useToast();

  const fetchProfile = async () => {
    setLoading(true);
    try {
      const token = sessionStorage.getItem("authToken");
      const res = await api.get("/profile", { headers: { Authorization: `Bearer ${token}` } });
      setProfileData(res.data);
    } catch (err: any) {
      toast({ title: "Error", description: err.response?.data?.error || "Failed to load profile.", variant: "destructive" });
    } finally { setLoading(false); }
  };

  const fetchStats = async () => {
    setLoading(true);
    try {
      const token = sessionStorage.getItem("authToken");
      const res = await api.get("/profile/stats", { headers: { Authorization: `Bearer ${token}` } });
      setStats(res.data);
    } catch (err: any) {
      toast({ title: "Error", description: err.response?.data?.error || "Failed to load stats.", variant: "destructive" });
    } finally { setLoading(false); }
  };

  useEffect(() => {
    fetchProfile();
    fetchStats();
  }, []);

  const handleProfileUpdate = async () => {
    setLoading(true);
    try {
      const token = sessionStorage.getItem("authToken");
      const res = await api.put("/profile", { name: profileData.name, username: profileData.username, email: profileData.email }, { headers: { Authorization: `Bearer ${token}` } });
      setProfileData(res.data);
      toast({ title: "Success", description: "Profile updated successfully!" });
    } catch (err: any) {
      toast({ title: "Error", description: err.response?.data?.error || "Failed to update profile.", variant: "destructive" });
    } finally { setLoading(false); }
  };

  const handlePasswordUpdate = async () => {
    if (!passwordData.currentPassword || !passwordData.newPassword) {
      toast({ title: "Warning", description: "Please fill all fields." });
      return;
    }
    setLoading(true);
    try {
      const token = sessionStorage.getItem("authToken");
      await api.put("/profile/password", passwordData, { headers: { Authorization: `Bearer ${token}` } });
      toast({ title: "Success", description: "Password updated successfully!" });
      setPasswordData({ currentPassword: "", newPassword: "" });
    } catch (err: any) {
      toast({ title: "Error", description: err.response?.data?.error || "Current password incorrect.", variant: "destructive" });
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-purple-50 to-blue-100 p-4 sm:p-6">
      <div className="max-w-6xl mx-auto flex flex-col md:flex-row gap-6">
        
        {/* Sidebar */}
        <div className="md:w-1/4 w-full flex flex-col gap-3 bg-white/80 backdrop-blur-md p-4 rounded-2xl shadow-md">
          <Button 
            variant={activeTab === "profile" ? "default" : "outline"} 
            className="w-full flex items-center gap-2 justify-center"
            onClick={() => setActiveTab("profile")}
          >
            <User className="w-4 h-4" /> Profile
          </Button>
          <Button 
            variant={activeTab === "update" ? "default" : "outline"} 
            className="w-full flex items-center gap-2 justify-center"
            onClick={() => setActiveTab("update")}
          >
            <Edit className="w-4 h-4" /> Update Profile
          </Button>
          <Button 
            variant={activeTab === "password" ? "default" : "outline"} 
            className="w-full flex items-center gap-2 justify-center"
            onClick={() => setActiveTab("password")}
          >
            <Lock className="w-4 h-4" /> Change Password
          </Button>
        </div>

        <div className="md:w-3/4 w-full space-y-6">
          <Card className="shadow-xl border-0 bg-white/90 backdrop-blur-lg rounded-2xl">
            <CardHeader className="border-b bg-gradient-to-r from-blue-500 to-purple-500 text-white rounded-t-2xl">
              <CardTitle className="text-xl font-semibold flex items-center gap-2">
                {activeTab === "profile" && <User className="w-5 h-5" />}
                {activeTab === "update" && <ShieldCheck className="w-5 h-5" />}
                {activeTab === "password" && <Lock className="w-5 h-5" />}
                {activeTab === "profile" ? "Profile Details" : activeTab === "update" ? "Update Profile" : "Change Password"}
              </CardTitle>
            </CardHeader>

            <CardContent className="p-6 space-y-6">
              {loading && <p className="text-gray-500 text-center">Loading...</p>}

              {activeTab === "profile" && !loading && (
                <div className="space-y-4 text-gray-700">
                  <p><strong>Name:</strong> {profileData.name}</p>
                  <p><strong>Username:</strong> {profileData.username}</p>
                  <p><strong>Email:</strong> {profileData.email}</p>
                  <p><strong>Role:</strong> {profileData.role}</p>

                  {stats && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-6">
                      <Card className="bg-blue-100/70 border-none text-center p-6 rounded-xl hover:scale-[1.03] transition-all">
                        <BarChart3 className="mx-auto text-blue-700 mb-2" />
                        <p className="text-3xl font-bold">{stats.totalCertificates}</p>
                        <p className="text-gray-600 mt-1">Total Certificates</p>
                      </Card>
                      <Card className="bg-purple-100/70 border-none text-center p-6 rounded-xl hover:scale-[1.03] transition-all">
                        <Lock className="mx-auto text-purple-700 mb-2" />
                        <p className="text-sm">{new Date(stats.lastLogin).toLocaleString()}</p>
                        <p className="text-gray-600 mt-1">Last Certificate Generated</p>
                      </Card>
                    </div>
                  )}
                </div>
              )}

              {activeTab === "update" && (
                <div className="space-y-4">
                  <Input 
                    type="text" 
                    placeholder="Full Name" 
                    value={profileData.name} 
                    onChange={(e) => setProfileData({ ...profileData, name: e.target.value })} 
                  />
                  <Input 
                    type="text" 
                    readOnly 
                    placeholder="Username" 
                    value={profileData.username} 
                    className="bg-gray-100 cursor-not-allowed" 
                  />
                  <Input 
                    type="email" 
                    placeholder="Email" 
                    value={profileData.email} 
                    onChange={(e) => setProfileData({ ...profileData, email: e.target.value })} 
                  />
                  <Button 
                    onClick={handleProfileUpdate} 
                    className="w-full bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transition-all"
                    disabled={loading}
                  >
                    Save Changes
                  </Button>
                </div>
              )}

              {activeTab === "password" && (
                <div className="space-y-4">
                  <Input 
                    type="password" 
                    placeholder="Current Password" 
                    value={passwordData.currentPassword} 
                    onChange={(e) => setPasswordData({ ...passwordData, currentPassword: e.target.value })} 
                  />
                  <Input 
                    type="password" 
                    placeholder="New Password" 
                    value={passwordData.newPassword} 
                    onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })} 
                  />
                  <Button 
                    onClick={handlePasswordUpdate} 
                    className="w-full bg-gradient-to-r from-green-600 to-emerald-700 hover:from-green-700 hover:to-emerald-800 transition-all"
                    disabled={loading}
                  >
                    Update Password
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default Profile;
