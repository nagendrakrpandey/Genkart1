"use client";

import { useState, useEffect } from "react";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Upload, ImageIcon, FileUp, Loader2, User } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

export default function UploadTemplate() {
  const [templateName, setTemplateName] = useState("");
  const [imageType, setImageType] = useState<string | number>("");
  const [selectedUser, setSelectedUser] = useState<any>(null);
  const [users, setUsers] = useState<any[]>([]);
  const [jrxmlFiles, setJrxmlFiles] = useState<File[]>([]);
  const [images, setImages] = useState<File[]>([]);
  const [imagePreviews, setImagePreviews] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const { toast } = useToast();

  const token =
    typeof window !== "undefined"
      ? sessionStorage.getItem("authToken")
      : null;

  // ✅ Fetch users list for dropdown
  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const res = await fetch("http://localhost:8086/profile/all", {
          headers: { Authorization: `Bearer ${token}` },
        });

        if (!res.ok) throw new Error(`Failed to fetch users: ${res.statusText}`);

        const data = await res.json();
        let normalized: any[] = [];

        if (Array.isArray(data)) {
          normalized = data.map((u: any) => ({
            id: u.id,
            username: u.username || u.userName || u.name || "Unknown",
          }));
        } else if (data?.users) {
          normalized = data.users.map((u: any) => ({
            id: u.id,
            username: u.username || u.userName || u.name || "Unknown",
          }));
        }

        setUsers(normalized);
      } catch (err: any) {
        toast({
          title: "Error loading users",
          description: err.message || "Unable to fetch users.",
          variant: "destructive",
        });
      }
    };

    if (token) fetchUsers();
  }, [token, toast]);

  // ✅ JRXML File Upload
  const handleJRXMLUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      setJrxmlFiles(Array.from(e.target.files));
    }
  };

  // ✅ Image Upload + Preview
  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const files = Array.from(e.target.files);
      setImages(files);
      setImagePreviews(files.map((file) => URL.createObjectURL(file)));
    }
  };

  // ✅ Submit Form
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!templateName.trim())
      return toast({
        title: "Template name required",
        variant: "destructive",
      });
    if (!imageType)
      return toast({ title: "Image type required", variant: "destructive" });
    if (!selectedUser)
      return toast({ title: "Select a user", variant: "destructive" });
    if (jrxmlFiles.length === 0)
      return toast({
        title: "No JRXML file selected",
        variant: "destructive",
      });

    setLoading(true);

    try {
      const formData = new FormData();
      formData.append("templateName", templateName);
      formData.append("imageType", String(imageType))
      formData.append("createdBy", String(selectedUser.id));

      jrxmlFiles.forEach((file) => formData.append("jrxml", file));
      images.forEach((file) => formData.append("images", file));

      const res = await fetch("http://localhost:8086/templates", {
        method: "POST",
        body: formData,
        headers: {
          Authorization: `Bearer ${token}`,
        } as any,
      });

      if (res.ok) {
        toast({
          title: "Upload Successful",
          description: "Template uploaded successfully!",
        });
        // Reset form
        setTemplateName("");
        setImageType("");
        setSelectedUser(null);
        setJrxmlFiles([]);
        setImages([]);
        setImagePreviews([]);
      } else {
        const errText = await res.text();
        toast({
          title: "Upload Failed",
          description: errText || "Server error occurred.",
          variant: "destructive",
        });
      }
    } catch (error: any) {
      toast({
        title: "Network Error",
        description: error.message || "Unable to connect to server.",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };


  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-indigo-50 flex items-center justify-center p-6">
      <Card className="w-full max-w-4xl bg-white border border-gray-200 shadow-2xl rounded-2xl transition-all">
        <CardHeader className="text-center border-b border-gray-100 pb-6">
          <CardTitle className="text-3xl font-semibold text-gray-800 tracking-tight">
            Upload Certificate Template
          </CardTitle>
          <p className="text-gray-500 text-sm mt-1">
            Managed by{" "}
            <span className="font-medium text-indigo-600">
              Nagendra Kumar Pandey
            </span>
          </p>
        </CardHeader>

        <CardContent className="p-8">
          <form
            onSubmit={handleSubmit}
            className="space-y-8"
            encType="multipart/form-data"
          >
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Template Name
                </label>
                <Input
                  type="text"
                  placeholder="Enter template name"
                  value={templateName}
                  onChange={(e) => setTemplateName(e.target.value)}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Image Type (Numeric)
                </label>
                <Input
                  type="number"
                  placeholder="e.g. 1, 2, 3"
                  value={imageType}
                  onChange={(e) => setImageType(e.target.value)}
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2 flex items-center gap-2">
                <User size={18} className="text-indigo-500" /> Select username
              </label>
              <select
                value={selectedUser?.id || ""}
                onChange={(e) => {
                  const selected = users.find(
                    (u) => String(u.id) === e.target.value
                  );
                  setSelectedUser(selected || null);
                }}
                className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:ring-indigo-500 focus:border-indigo-500"
              >
                <option value="">-- Select a Username --</option>
                {users.length > 0 ? (
                  users.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.username}
                    </option>
                  ))
                ) : (
                  <option disabled>Loading usernames...</option>
                )}
              </select>

              {selectedUser && (
                <p className="text-sm text-indigo-600 mt-1">
                  Selected: {selectedUser.username} (ID: {selectedUser.id})
                </p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2 flex items-center gap-2">
                <FileUp size={18} className="text-indigo-500" /> Upload JRXML
                File(s)
              </label>
              <Input
                type="file"
                accept=".jrxml"
                multiple
                onChange={handleJRXMLUpload}
              />
              {jrxmlFiles.length > 0 && (
                <p className="text-xs text-gray-500 mt-1">
                  {jrxmlFiles.length} file(s) selected
                </p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2 flex items-center gap-2">
                <ImageIcon size={18} className="text-indigo-500" /> Upload
                Image(s)
              </label>
              <Input
                type="file"
                accept="image/*"
                multiple
                onChange={handleImageUpload}
              />
              {imagePreviews.length > 0 && (
                <div className="grid grid-cols-3 sm:grid-cols-4 gap-3 mt-3">
                  {imagePreviews.map((src, index) => (
                    <img
                      key={index}
                      src={src}
                      alt={`preview-${index}`}
                      className="object-cover w-full h-24 rounded-lg border"
                    />
                  ))}
                </div>
              )}
            </div>

            <div className="pt-6 text-center">
              <Button
                type="submit"
                disabled={loading}
                className="bg-indigo-600 hover:bg-indigo-700 text-white px-8 py-3 rounded-lg font-medium shadow-md hover:shadow-lg transition-all"
              >
                {loading ? (
                  <>
                    <Loader2 className="animate-spin h-5 w-5 mr-2" /> Uploading...
                  </>
                ) : (
                  <>
                    <Upload size={18} className="mr-2" /> Upload Template
                  </>
                )}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
