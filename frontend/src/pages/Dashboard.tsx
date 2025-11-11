import { useEffect, useState } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from "recharts";
import { Award, Download, TrendingUp, FileText, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useNavigate } from "react-router-dom";

export default function Dashboard() {
  const [reports, setReports] = useState<any[]>([]);
  const [totalCertificates, setTotalCertificates] = useState(0);
  const [totalTemplates, setTotalTemplates] = useState(0);
  const [thisMonth, setThisMonth] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const token = sessionStorage.getItem("authToken");
  const navigate = useNavigate();

  const fetchWithAuth = async (url: string, options: any = {}) => {
    const headers = {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...options.headers,
    };

    const response = await fetch(url, { ...options, headers });
    if (response.status === 403) throw new Error("Access Denied (403)");
    if (!response.ok) throw new Error(`Request failed: ${response.status}`);
    return response.json();
  };

  const generateCertificates = async () => {
    try {
      if (!token) {
        setError("User not logged in. Please sign in first.");
        return navigate("/login");
      }
      setLoading(true);
      setError("");

      const data = await fetchWithAuth(
        "http://localhost:8086/certificates/generate",
        { method: "POST" }
      );

      setReports(data);
      setTotalCertificates(data.length);
    } catch (err: any) {
      console.error("Error during certificate generation:", err);
      setError(
        err.message === "Access Denied (403)"
          ? "You don't have permission to generate certificates. Contact admin."
          : "An error occurred while generating certificates. Please try again later."
      );
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    if (!token) {
      setError("User not logged in. Redirecting to login...");
      setTimeout(() => navigate("/login"), 2000);
      return;
    }

    const fetchData = async () => {
      try {
        const [reportsData, monthCount, templateCount] = await Promise.all([
          fetchWithAuth("http://localhost:8086/reports/all"),
          fetchWithAuth("http://localhost:8086/reports/count/month"),
          fetchWithAuth("http://localhost:8086/templates/count"),
        ]);
        setReports(reportsData);
        setTotalCertificates(reportsData.length);
        setThisMonth(monthCount);
        setTotalTemplates(templateCount);
      } catch (err) {
        console.error("Error fetching dashboard data:", err);
        setError("Failed to fetch dashboard data. Please check your login or API.");
      }
    };

    fetchData();
  }, [token, navigate]);

  const monthlyData = Array.from({ length: 12 }, (_, i) => {
    const monthReports = reports.filter(
      (r) => r.generatedOn && new Date(r.generatedOn).getMonth() === i
    );
    return {
      month: new Date(0, i).toLocaleString("default", { month: "short" }),
      certificates: monthReports.length,
    };
  });

  const types = [...new Set(reports.map((r) => r.templateName).filter(Boolean))];
  const certificateTypes = types.map((type, idx) => {
    const count = reports.filter((r) => r.templateName === type).length;
    const colors = ["#4ade80", "#60a5fa", "#facc15", "#f87171", "#a78bfa"];
    return { name: type, value: count, color: colors[idx % colors.length] };
  });

  const quickActions = [
    {
      title: loading ? "Processing..." : "Generate Certificates",
      description: "Create new certificates instantly",
      icon: Award,
      color: "from-primary to-primary-glow",
      onClick: () => navigate("/Certificates"),
    },
    {
      title: "View Reports",
      description: "Check certificate reports & analytics",
      icon: FileText,
      color: "from-chart-2 to-green-400",
      onClick: () => navigate("/reports"),
    },
    {
      title: "Manage Profile",
      description: "Update your personal settings",
      icon: Users,
      color: "from-chart-3 to-yellow-400",
      onClick: () => navigate("/profile"),
    },
  ];

  return (
    <div className="space-y-8 p-4 md:p-6 lg:p-8">
      <div className="text-center md:text-left">
        <h1 className="text-3xl md:text-4xl font-bold tracking-tight text-gray-900">
          Dashboard
        </h1>
        <p className="text-gray-500 mt-1">
          Hey <span className="font-semibold text-indigo-500">Nagendra Kumar Pandey</span>! 👋
          Track and manage certificates, templates, and performance in real-time.
        </p>
        {error && <p className="text-red-500 mt-3 font-semibold">{error}</p>}
      </div>

      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard
          title="Total Templates"
          value={totalTemplates}
          icon={<Award className="h-5 w-5 text-white" />}
          color="bg-gradient-to-r from-indigo-500 to-indigo-400"
        />
        <StatCard
          title="Total Certificates"
          value={totalCertificates}
          icon={<Download className="h-5 w-5 text-white" />}
          color="bg-gradient-to-r from-green-400 to-green-300"
        />
        <StatCard
          title="This Month"
          value={thisMonth}
          icon={<TrendingUp className="h-5 w-5 text-white" />}
          color="bg-gradient-to-r from-pink-500 to-pink-400"
        />
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <CardChart
          title="Monthly Certificate Generation"
          description="Certificates generated per month"
        >
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={monthlyData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="month" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="certificates" fill="#4ade80" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </CardChart>

        <CardChart
          title="Certificate Type Distribution"
          description="Breakdown by template type"
        >
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={certificateTypes}
                cx="50%"
                cy="50%"
                innerRadius={60}
                outerRadius={120}
                paddingAngle={5}
                dataKey="value"
              >
                {certificateTypes.map((entry, index) => (
                  <Cell key={index} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
          <div className="flex flex-wrap justify-center gap-4 mt-4">
            {certificateTypes.map((type) => (
              <div key={type.name} className="flex items-center space-x-2">
                <div
                  className="w-3 h-3 rounded-full"
                  style={{ backgroundColor: type.color }}
                />
                <span className="text-sm text-gray-600">
                  {type.name} ({type.value})
                </span>
              </div>
            ))}
          </div>
        </CardChart>
      </div>

      <Card className="hover:shadow-xl transition-shadow duration-300">
        <CardHeader>
          <CardTitle>Quick Actions</CardTitle>
          <CardDescription>Access common features quickly</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {quickActions.map((action) => (
              <div
                key={action.title}
                className="group relative overflow-hidden rounded-xl border p-6 hover:shadow-lg transition-all duration-300"
              >
                <div
                  className={`absolute inset-0 bg-gradient-to-br ${action.color} opacity-10 group-hover:opacity-20 transition-opacity`}
                />
                <div className="relative">
                  <action.icon className="h-8 w-8 mb-3 text-primary" />
                  <h3 className="font-semibold mb-1 text-gray-800">
                    {action.title}
                  </h3>
                  <p className="text-sm text-gray-500 mb-4">
                    {action.description}
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={loading}
                    className="w-full group-hover:bg-primary group-hover:text-white transition-colors"
                    onClick={action.onClick}
                  >
                    {loading && action.title.includes("Generate")
                      ? "Processing..."
                      : "Get Started"}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
const StatCard = ({ title, value, icon, color }) => (
  <Card
    className={`flex flex-col justify-between p-4 rounded-xl shadow-md text-white ${color} hover:scale-105 transition-transform duration-300`}
  >
    <div className="flex items-center justify-between">
      <h3 className="text-sm font-medium">{title}</h3>
      {icon}
    </div>
    <div className="text-2xl font-bold mt-4">{value}</div>
  </Card>
);

const CardChart = ({ title, description, children }) => (
  <Card className="p-4 rounded-xl shadow-md hover:shadow-xl transition-shadow duration-300">
    <CardHeader>
      <CardTitle>{title}</CardTitle>
      <CardDescription>{description}</CardDescription>
    </CardHeader>
    <CardContent>{children}</CardContent>
  </Card>
);
