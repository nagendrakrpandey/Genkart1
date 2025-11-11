"use client";

import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import api from "@/Services/api";

export default function UserRegistrationForm() {
  const [formData, setFormData] = useState({
    username: "",
    name: "",
    email: "",
    password: "",
    confirmPassword: "",
    role: "",
  });

  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const token = sessionStorage.getItem("authToken");
    if (token) {
      console.log("Existing session token:", token);
    }
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    if (formData.password !== formData.confirmPassword) {
      alert(" Passwords do not match!");
      setLoading(false);
      return;
    }

    try {
      const response = await api.post("/profile/register", formData);
      const data = response.data;

      if (response.status === 201) {
        alert("User registered successfully!");

        if (data.token) {
          sessionStorage.setItem("authToken", data.token);
          console.log("Token saved to sessionStorage:", data.token);
        } else {
          console.warn("No token received from backend.");
        }

        setFormData({
          username: "",
          name: "",
          email: "",
          password: "",
          confirmPassword: "",
          role: "",
        });
      }
    } catch (error: any) {
        const msg = error.response?.data || "Unknown error";
        alert(` Registration failed: ${msg}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-600 via-purple-600 to-pink-500 p-6">
      <motion.div
        initial={{ opacity: 0, y: 40 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6 }}
        className="bg-white w-full max-w-2xl rounded-2xl shadow-2xl p-8"
      >
        <h2 className="text-3xl font-bold text-center text-gray-800 mb-8">
          User Registration
        </h2>

        <form onSubmit={handleSubmit} autoComplete="off" className="space-y-5">
          <input type="text" name="hidden" autoComplete="off" className="hidden" />
          <input type="password" name="hiddenPass" autoComplete="new-password" className="hidden" />

          <div className="grid gap-6 sm:grid-cols-1 lg:grid-cols-2">
            <div>
              <label className="block text-gray-700 mb-1">Username</label>
              <input
                type="text"
                name="username"
                required
                value={formData.username}
                onChange={handleChange}
                autoComplete="off"
                className="w-full border border-gray-300 px-4 py-2 rounded-lg focus:ring-2 focus:ring-blue-500"
                placeholder="Enter username"
              />
            </div>

            <div>
              <label className="block text-gray-700 mb-1">Full Name</label>
              <input
                type="text"
                name="name"
                required
                value={formData.name}
                onChange={handleChange}
                autoComplete="off"
                className="w-full border border-gray-300 px-4 py-2 rounded-lg focus:ring-2 focus:ring-blue-500"
                placeholder="Enter full name"
              />
            </div>

            <div>
              <label className="block text-gray-700 mb-1">Role</label>
              <select
                name="role"
                value={formData.role}
                onChange={handleChange}
                required
                className="w-full border border-gray-300 px-4 py-2 rounded-lg focus:ring-2 focus:ring-blue-500"
              >
                <option value="">Select Role</option>
                <option value="ADMIN">Admin</option>
                <option value="USER">User</option>
              </select>
            </div>

            <div>
              <label className="block text-gray-700 mb-1">Email</label>
              <input
                type="email"
                name="email"
                required
                value={formData.email}
                onChange={handleChange}
                autoComplete="off"
                className="w-full border border-gray-300 px-4 py-2 rounded-lg focus:ring-2 focus:ring-blue-500"
                placeholder="example@email.com"
              />
            </div>

            <div>
              <label className="block text-gray-700 mb-1">Password</label>
              <input
                type="password"
                name="password"
                required
                value={formData.password}
                onChange={handleChange}
                autoComplete="new-password"
                className="w-full border border-gray-300 px-4 py-2 rounded-lg focus:ring-2 focus:ring-blue-500"
                placeholder="********"
              />
            </div>

            <div>
              <label className="block text-gray-700 mb-1">Confirm Password</label>
              <input
                type="password"
                name="confirmPassword"
                required
                value={formData.confirmPassword}
                onChange={handleChange}
                autoComplete="new-password"
                className="w-full border border-gray-300 px-4 py-2 rounded-lg focus:ring-2 focus:ring-blue-500"
                placeholder="********"
              />
            </div>
          </div>

          <motion.button
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.9 }}
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white py-2 font-semibold rounded-lg shadow-md hover:bg-blue-700 transition duration-200"
          >
            {loading ? "Registering..." : "Register"}
          </motion.button>
        </form>
      </motion.div>
    </div>
  );
}
