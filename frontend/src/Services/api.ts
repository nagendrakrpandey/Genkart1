
import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8086",
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: false, 
});

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem("authToken"); 
  if (token) {
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error("API error:", error.response?.data || error.message);
    return Promise.reject(error);
  }
);

export default api;

