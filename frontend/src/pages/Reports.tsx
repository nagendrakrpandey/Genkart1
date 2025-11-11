"use client";

import React, { useState, useEffect } from "react";
import api from "@/Services/api";
import DataTable, { TableColumn } from "react-data-table-component";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/use-toast";
import DatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import * as XLSX from "xlsx";

interface ReportData {
  id: number;
  sid?: string;
  courseName?: string;
  templateName?: string;
  jobrole?: string;
  level?: string;
  batchId?: string;
  trainingPartner?: string;
  generatedById?: number;
  userProfileId?: number;
  generatedOn?: string;
  status?: string;
  grade?: string;
}

interface ReportsPageProps {
  isAdmin: boolean;
  userId?: number;
}

export default function ReportsPage({ isAdmin, userId }: ReportsPageProps) {
  const { toast } = useToast();
  const [reports, setReports] = useState<ReportData[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [fromDate, setFromDate] = useState<Date | null>(null);
  const [toDate, setToDate] = useState<Date | null>(null);
  const [totalRows, setTotalRows] = useState(0);
  const [dataLoaded, setDataLoaded] = useState(false);

  const fetchReports = async () => {
    if (!fromDate || !toDate) {
      toast({
        title: "Warning",
        description: "Please select both From Date and To Date",
        variant: "destructive",
      });
      return;
    }

    setLoading(true);
    try {
      const params: any = {};
      if (searchTerm) params.searchTerm = searchTerm;
      params.fromDate = fromDate.toISOString().split("T")[0];
      params.toDate = toDate.toISOString().split("T")[0];

      console.log("Fetching reports with params:", params);

      const res = await api.get("/reports/filter", { params });

      console.log("API Response:", res.data);

      const data: ReportData[] = res.data.map((r: any) => ({
        id: r.id,
        sid: r.sid,
        courseName: r.courseName,
        templateName: r.templateName,
        jobrole: r.jobrole,
        level: r.level,
        batchId: r.batchId,
        trainingPartner: r.trainingPartner,
        generatedById: r.generatedById,
        userProfileId: r.userProfileId,
        generatedOn: r.generatedOn,
        status: r.status,
        grade: r.grade,
      }));

      setReports(data);
      setTotalRows(data.length);
      setDataLoaded(true);

      toast({
        title: "Success",
        description: `Loaded ${data.length} reports for selected date range`,
        variant: "default",
      });

    } catch (err: any) {
      console.error("Error fetching reports:", err);
      toast({
        title: "Error",
        description: err.response?.data?.message || err.response?.data?.error || "Failed to load reports.",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };


  const fetchAllReports = async () => {
    setLoading(true);
    try {
      const params: any = {};
      if (searchTerm) params.searchTerm = searchTerm;

      console.log("Fetching all reports with params:", params);

      const res = await api.get("/reports/all", { params });

      console.log("All Reports API Response:", res.data);

      const data: ReportData[] = res.data.map((r: any) => ({
        id: r.id,
        sid: r.sid,
        courseName: r.courseName,
        templateName: r.templateName,
        jobrole: r.jobrole,
        level: r.level,
        batchId: r.batchId,
        trainingPartner: r.trainingPartner,
        generatedById: r.generatedById,
        userProfileId: r.userProfileId,
        generatedOn: r.generatedOn,
        status: r.status,
        grade: r.grade,
      }));

      setReports(data);
      setTotalRows(data.length);
      setDataLoaded(true);
      setFromDate(null);
      setToDate(null);

      toast({
        title: "Success",
        description: `Loaded ${data.length} reports (all data)`,
        variant: "default",
      });

    } catch (err: any) {
      console.error("Error fetching all reports:", err);
      toast({
        title: "Error",
        description: err.response?.data?.message || err.response?.data?.error || "Failed to load reports.",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const resetFilters = () => {
    setFromDate(null);
    setToDate(null);
    setSearchTerm("");
    setReports([]);
    setTotalRows(0);
    setDataLoaded(false);
  };


  const exportExcel = () => {
    if (reports.length === 0) {
      toast({
        title: "Info",
        description: "No data to export",
        variant: "default",
      });
      return;
    }

    const worksheetData = reports.map((r, index) => ({
      "S.No": index + 1,
      SID: r.sid || "N/A",
      Template: r.templateName || "N/A",
      "Job Role": r.jobrole || "N/A",
      "Candidate Name": r.courseName || "N/A",
      Level: r.level || "N/A",
      Batch: r.batchId || "N/A",
      "Training Partner": r.trainingPartner || "N/A",
      Grade: r.grade || "N/A",
      Status: r.status || "N/A",
      "Generated By": r.generatedById || "N/A",
      "Generated On": r.generatedOn || "N/A",
    }));

    const worksheet = XLSX.utils.json_to_sheet(worksheetData);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Reports");
    
   
    const maxWidth = worksheetData.reduce((w, r) => Math.max(w, r['Candidate Name']?.length || 0), 10);
    worksheet['!cols'] = [{ wch: 5 }, { wch: 15 }, { wch: 20 }, { wch: 15 }, { wch: 25 }, { wch: 10 }, { wch: 15 }, { wch: 20 }, { wch: 10 }, { wch: 10 }, { wch: 15 }, { wch: 20 }];
    
    XLSX.writeFile(workbook, `certificate_reports_${new Date().toISOString().split('T')[0]}.xlsx`);
    
    toast({
      title: "Success",
      description: `Exported ${reports.length} records to Excel`,
      variant: "default",
    });
  };

  const columns: TableColumn<ReportData>[] = [
    { 
      name: "S.No", 
      cell: (_row, index) => index + 1, 
      width: "70px",
      sortable: true 
    },
    { 
      name: "SID", 
      selector: (row) => row.sid || "N/A", 
      sortable: true,
      wrap: true 
    },
    { 
      name: "Template", 
      selector: (row) => row.templateName || "N/A", 
      sortable: true,
      wrap: true 
    },
    { 
      name: "Job Role", 
      selector: (row) => row.jobrole || "N/A", 
      sortable: true,
      wrap: true 
    },
    { 
      name: "Candidate Name", 
      selector: (row) => row.courseName || "N/A", 
      sortable: true,
      wrap: true 
    },
    { 
      name: "Level", 
      selector: (row) => row.level || "N/A",
      wrap: true 
    },
    { 
      name: "Batch", 
      selector: (row) => row.batchId || "N/A", 
      sortable: true,
      wrap: true 
    },
    { 
      name: "Training Partner", 
      selector: (row) => row.trainingPartner || "N/A", 
      sortable: true,
      wrap: true 
    },
    { 
      name: "Grade", 
      selector: (row) => row.grade || "N/A",
      wrap: true 
    },
    { 
      name: "Status", 
      selector: (row) => row.status || "N/A",
      wrap: true 
    },
    { 
      name: "Generated By", 
      selector: (row) => row.generatedById || "N/A", 
      sortable: true,
      wrap: true 
    },
    { 
      name: "Generated On", 
      selector: (row) => {
        if (!row.generatedOn) return "N/A";
        try {
          const date = new Date(row.generatedOn);
          return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
        } catch {
          return row.generatedOn;
        }
      },
      wrap: true,
      sortable: true 
    },
  ];

  return (
    <div className="p-4 md:p-6 max-w-7xl mx-auto space-y-6">
      <div className="flex flex-col md:flex-row md:justify-between md:items-center gap-4">
        <h1 className="text-3xl font-bold text-gray-800">Certificate Reports</h1>
        <div className="text-sm text-gray-600">
          {dataLoaded ? `Showing ${reports.length} records` : "Select date range to load reports"}
        </div>
      </div>

      <div className="bg-white p-4 rounded-lg shadow">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">From Date</label>
            <DatePicker
              selected={fromDate}
              onChange={(date: Date | null) => setFromDate(date)}
              selectsStart
              startDate={fromDate}
              endDate={toDate}
              placeholderText="Select start date"
              className="w-full border rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-teal-500"
              dateFormat="yyyy-MM-dd"
              isClearable
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">To Date</label>
            <DatePicker
              selected={toDate}
              onChange={(date: Date | null) => setToDate(date)}
              selectsEnd
              startDate={fromDate}
              endDate={toDate}
              minDate={fromDate}
              placeholderText="Select end date"
              className="w-full border rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-teal-500"
              dateFormat="yyyy-MM-dd"
              isClearable
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Search</label>
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search by SID, Name, Template..."
              className="w-full border rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-teal-500"
            />
          </div>
          <div className="flex items-end gap-2">
            <Button 
              variant="default" 
              onClick={fetchReports} 
              className="w-full"
              disabled={!fromDate || !toDate}
            >
              Filter by Date
            </Button>
          </div>
        </div>
        
        <div className="flex flex-wrap gap-2">
          <Button variant="default" onClick={fetchAllReports}>
            Load All Reports
          </Button>
          <Button variant="outline" onClick={resetFilters}>
            Clear Filters
          </Button>
          <Button 
            variant="default" 
            onClick={exportExcel} 
            disabled={reports.length === 0}
          >
            Export Excel ({reports.length})
          </Button>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <DataTable
          columns={columns}
          data={reports}
          progressPending={loading}
          pagination
          paginationPerPage={50}
          paginationRowsPerPageOptions={[10, 25, 50, 100, 500, 1000]}
          paginationComponentOptions={{
            rowsPerPageText: "Rows per page:",
            rangeSeparatorText: "of",
            noRowsPerPage: false,
          }}
          highlightOnHover
          persistTableHead
          responsive
          striped
          dense
          noDataComponent={
            <div className="p-8 text-center text-gray-500">
              {loading 
                ? "Loading reports..." 
                : dataLoaded 
                  ? "No reports found for the selected criteria." 
                  : "Please select date range and click 'Filter by Date' to load reports."
              }
            </div>
          }
          customStyles={{
            headCells: {
              style: {
                backgroundColor: '#f8fafc',
                fontWeight: 'bold',
                fontSize: '14px',
              },
            },
            cells: {
              style: {
                fontSize: '13px',
              },
            },
          }}
        />
      </div>
    </div>
  );
}