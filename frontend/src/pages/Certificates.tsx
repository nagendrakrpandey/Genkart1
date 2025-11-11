"use client";

import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/hooks/use-toast";
import {
  FileSpreadsheet,
  Image,
  Clock,
  CheckCircle,
  FileImage,
  Stamp,
  UploadCloud,
} from "lucide-react";
import axios from "axios";

interface TemplateType {
  id: number;
  templateName: string;
  imageType: number;
}

interface TemplateFiles {
  excel: File | null;
  zip: File | null;
  logo: File | null;
  sign: File | null;
}

function UploadCard({
  title,
  icon,
  accept,
  onChange,
  file,
  preview,
}: {
  title: string;
  icon: React.ReactNode;
  accept: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  file: File | null;
  preview?: boolean;
}) {
  return (
    <div className="flex flex-col items-center bg-white rounded-2xl shadow-lg p-4 border border-gray-200 hover:shadow-xl transition w-full">
      <div className="text-indigo-600 mb-2">{icon}</div>
      <label className="text-sm font-semibold mb-2">{title}</label>
      <Input
        type="file"
        accept={accept}
        onChange={onChange}
        className="w-full max-w-xs text-sm cursor-pointer border-dashed border-2 border-indigo-400 hover:border-indigo-600 transition"
      />
      {file && (
        <div className="flex flex-col items-center mt-3 w-full">
          <div className="flex items-center gap-2 bg-indigo-50 p-2 rounded-lg w-full justify-between">
            <span className="truncate text-sm font-medium">{file.name}</span>
            <Badge className="bg-indigo-500 text-white">Ready</Badge>
          </div>
          {preview && file.type.startsWith("image/") && (
            <img
              src={URL.createObjectURL(file)}
              alt="preview"
              className="h-20 object-contain mt-2 rounded-md border shadow-sm"
            />
          )}
        </div>
      )}
    </div>
  );
}

export default function CertificatePage() {
  const navigate = useNavigate();
  const [templates, setTemplates] = useState<TemplateType[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [templateImages, setTemplateImages] = useState<string[]>([]);
  const [files, setFiles] = useState<TemplateFiles>({
    excel: null,
    zip: null,
    logo: null,
    sign: null,
  });
  const [isUploading, setIsUploading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [role, setRole] = useState<string>("");
  const { toast } = useToast();

  const token =
    typeof window !== "undefined" ? sessionStorage.getItem("authToken") : null;

  useEffect(() => {
    if (!token) {
      toast({
        title: "Unauthorized",
        description: "Please login again.",
        variant: "destructive",
      });
      return;
    }
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      setRole(payload.role?.toUpperCase() || "USER");
    } catch (e) {
      console.error("JWT parse error", e);
    }

    axios
      .get("http://localhost:8086/templates", {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then((res) => setTemplates(res.data || []))
      .catch((err) => {
        console.error("Error fetching templates:", err);
        toast({
          title: "Error loading templates",
          description: err.response?.data || err.message,
          variant: "destructive",
        });
      });
  }, [token]);

  useEffect(() => {
    if (!selectedTemplateId || !token) {
      setTemplateImages([]);
      return;
    }
    axios
      .get<string[]>(`http://localhost:8086/templates/${selectedTemplateId}`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then((res) => setTemplateImages(res.data || []))
      .catch((err) => {
        console.error("Error fetching template images:", err);
        toast({
          title: "Error loading images",
          description: err.response?.data || err.message,
          variant: "destructive",
        });
      });
  }, [selectedTemplateId, token]);

  const selectedTemplate = templates.find((t) => t.id === selectedTemplateId);

  const requiredFields = (() => {
    if (!selectedTemplate) return [];
    const type = selectedTemplate.imageType;
    const fields: (keyof TemplateFiles)[] = [];
    if (type >= 0) fields.push("excel");
    if (type >= 1 && type <= 3) {
      if (type >= 1) fields.push("zip");
      if (type >= 2) fields.push("logo");
      if (type >= 3) fields.push("sign");
    }
    if (type === 4 || type === 5) {
      return ["excel"];
    }
    return fields;
  })();

  const handleFileUpload = (
    type: keyof TemplateFiles,
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = e.target.files?.[0] || null;
    if (file) {
      setFiles((prev) => ({ ...prev, [type]: file }));
      toast({ title: `${type.toUpperCase()} uploaded`, description: file.name });
    }
  };

  const handleGenerate = async () => {
    if (!selectedTemplate || !token) return;

    const missing = requiredFields.filter((f) => !files[f]);
    if (missing.length > 0) {
      toast({
        title: "Missing files",
        description: `Please upload: ${missing.join(", ")}`,
        variant: "destructive",
      });
      return;
    }

    const formData = new FormData();
    const fieldMap: Record<keyof TemplateFiles, string> = {
      excel: "excel",
      zip: "zipImage",
      logo: "logo",
      sign: "sign",
    };
    requiredFields.forEach((f) => {
      const file = files[f];
      if (file) formData.append(fieldMap[f], file);
    });

    setIsUploading(true);
    setErrorMessage("");
    try {
      const res = await axios.post(
        `http://localhost:8086/certificates/generate-zip/${selectedTemplate.id}`,
        formData,
        {
          responseType: "blob",
          headers: {
            "Content-Type": "multipart/form-data",
            Authorization: `Bearer ${token}`,
          },
        }
      );

      const url = window.URL.createObjectURL(
        new Blob([res.data], { type: "application/zip" })
      );
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", "Certificates.zip");
      document.body.appendChild(link);
      link.click();
      link.remove();

      toast({ title: "Certificates generated successfully!" });
      setFiles({ excel: null, zip: null, logo: null, sign: null });
      setSelectedTemplateId(null);
      setTemplateImages([]);
    } catch (err: any) {
      console.error(err);
      let backendMessage = "An error occurred while generating certificates.";
      if (err.response) {
        if (err.response.data?.message) {
          backendMessage = err.response.data.message;
        } else if (typeof err.response.data === "string") {
          backendMessage = err.response.data;
        }
      } else if (err.message) {
        backendMessage = err.message;
      }
      setErrorMessage(backendMessage);
      toast({
        title: "Error",
        description: backendMessage,
        variant: "destructive",
      });
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <>
      {isUploading && (
        <div className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-purple-50 backdrop-blur-md transition-opacity animate-fadeIn">
          <div className="relative flex items-center justify-center mb-6">
            <div className="absolute h-24 w-24 rounded-full bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 opacity-40 blur-xl animate-pulse"></div>
            <div className="h-14 w-14 border-4 border-transparent border-t-indigo-600 border-r-purple-600 rounded-full animate-spin"></div>
          </div>
          <h2 className="text-2xl font-semibold bg-gradient-to-r from-indigo-600 to-purple-600 bg-clip-text text-transparent animate-pulse">
            Generating Certificates...
          </h2>
          <p className="mt-2 text-gray-500 text-sm tracking-wide animate-[pulse_2s_ease-in-out_infinite]">
            Please wait while we prepare your files
          </p>
        </div>
      )}

      <div className="min-h-screen bg-gradient-to-br from-indigo-100 via-white to-purple-100 py-10 px-4 flex justify-center items-center">
        <Card className="w-full max-w-7xl shadow-2xl rounded-3xl p-6 bg-white border border-gray-200">
          <CardHeader className="text-center mb-6">
            <CardTitle className="text-3xl md:text-4xl font-extrabold bg-gradient-to-r from-indigo-600 to-purple-600 bg-clip-text text-transparent">
              Certificate Management
            </CardTitle>
            <p className="text-sm text-gray-500 mt-1">Role: {role}</p>

            {role === "ADMIN" && (
              <div className="flex justify-center mt-4">
                <Button
                  className="bg-gradient-to-r from-purple-600 to-indigo-600 text-white px-6 py-2 rounded-full flex items-center gap-2 hover:scale-105 transition"
                  onClick={() => navigate("/upload-template")}
                >
                  <UploadCloud className="h-5 w-5" /> Upload New Template
                </Button>
              </div>
            )}
          </CardHeader>

          <CardContent className="space-y-6">
            <div className="flex flex-col items-center gap-2">
              <select
                className="w-full md:w-[85%] border rounded-lg p-3 text-sm focus:ring focus:ring-indigo-400 shadow-sm"
                value={selectedTemplateId ?? ""}
                onChange={(e) => setSelectedTemplateId(Number(e.target.value))}
              >
                <option value="">-- Select Certificate Template --</option>
                {templates.length > 0 ? (
                  templates.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.templateName}
                    </option>
                  ))
                ) : (
                  <option disabled>No templates found</option>
                )}
              </select>
              {selectedTemplate && (
                <p className="text-xs text-gray-500 mt-1">
                  Required Files: {requiredFields.join(", ")}
                </p>
              )}
            </div>

            {templateImages.length > 0 && (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 mt-4">
                {templateImages.map((img, idx) => (
                  <img
                    key={idx}
                    src={img}
                    alt={`Template ${idx + 1}`}
                    className="h-28 sm:h-32 md:h-40 w-full object-contain rounded-xl border hover:shadow-md transition"
                  />
                ))}
              </div>
            )}

            {selectedTemplate && (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mt-6">
                {requiredFields.includes("excel") && (
                  <UploadCard
                    title="Upload Excel"
                    icon={<FileSpreadsheet className="h-6 w-6 text-green-600" />}
                    accept=".xlsx,.xls"
                    onChange={(e) => handleFileUpload("excel", e)}
                    file={files.excel}
                  />
                )}
                {requiredFields.includes("zip") && (
                  <UploadCard
                    title="Upload Images (ZIP)"
                    icon={<Image className="h-6 w-6 text-purple-600" />}
                    accept=".zip"
                    onChange={(e) => handleFileUpload("zip", e)}
                    file={files.zip}
                  />
                )}
                {requiredFields.includes("logo") && (
                  <UploadCard
                    title="Upload Logo"
                    icon={<FileImage className="h-6 w-6 text-blue-600" />}
                    accept="image/*"
                    onChange={(e) => handleFileUpload("logo", e)}
                    file={files.logo}
                    preview
                  />
                )}
                {requiredFields.includes("sign") && (
                  <UploadCard
                    title="Upload Signature"
                    icon={<Stamp className="h-6 w-6 text-amber-600" />}
                    accept="image/*"
                    onChange={(e) => handleFileUpload("sign", e)}
                    file={files.sign}
                    preview
                  />
                )}
              </div>
            )}

            {selectedTemplate && (
              <div className="text-center mt-8">
                <Button
                  onClick={handleGenerate}
                  disabled={isUploading || requiredFields.some((f) => !files[f])}
                  className="px-8 py-3 text-lg rounded-full bg-gradient-to-r from-indigo-600 to-purple-600 hover:shadow-xl text-white disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200"
                >
                  {isUploading ? (
                    <>
                      <Clock className="h-5 w-5 mr-2 animate-spin" /> Processing...
                    </>
                  ) : (
                    <>
                      <CheckCircle className="h-5 w-5 mr-2" /> Generate Certificates
                    </>
                  )}
                </Button>
                {errorMessage && (
                  <p className="text-red-600 mt-3 font-medium text-sm">
                    {errorMessage}
                  </p>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
