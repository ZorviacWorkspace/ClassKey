import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "motion/react";
import type { LucideIcon } from "lucide-react";
import {
  Fingerprint, QrCode, MapPin, Clock, CheckCircle2, XCircle,
  Bell, User, ChevronRight, Calendar, Download, Mail, Search,
  AlertTriangle, Settings, Home, Users, BarChart3, ArrowLeft, Plus,
  Moon, Sun, Shield, LogOut, Check, X, Scan, FileText, Send,
  History, RefreshCw, MoreHorizontal, GraduationCap, ClipboardList,
  CheckCheck, Smartphone, Eye,
} from "lucide-react";
import {
  BarChart as ReBarChart, Bar, XAxis, YAxis, ResponsiveContainer, Tooltip,
} from "recharts";
import classKeyLogo from "@/imports/ChatGPT_Image_Jul_3__2026__07_32_24_PM.png";

// ─── Types ─────────────────────────────────────────────────────────────────

type Role = "student" | "staff";
type StudentScreen = "home" | "history" | "permissions" | "notifications" | "profile";
type StaffScreen = "dashboard" | "students" | "reports" | "qr" | "settings";
type Overlay =
  | "mark-attendance"
  | "attendance-success"
  | "new-permission"
  | "suspicious"
  | "approvals"
  | null;

// ─── Mock Data ──────────────────────────────────────────────────────────────

const STUDENT_USER = {
  name: "Priya Sharma",
  rollNo: "CS21001",
  dept: "Computer Science",
  year: "3rd Year · Section A",
  attendancePct: 84,
  initials: "PS",
};

const STAFF_USER = {
  name: "Dr. Rajesh Kumar",
  dept: "Computer Science",
  role: "Associate Professor",
  initials: "RK",
};

const TODAY_CLASS = {
  subject: "Data Structures & Algorithms",
  code: "CS301",
  room: "LH-204",
  time: "09:00 – 10:00 AM",
  graceLeft: 7 * 60,
};

const HISTORY = [
  { date: "Mon, 30 Jun", status: "present", time: "08:42 AM", subject: "Data Structures" },
  { date: "Fri, 27 Jun", status: "present", time: "08:39 AM", subject: "Operating Systems" },
  { date: "Thu, 26 Jun", status: "late",    time: "09:11 AM", subject: "Database Systems"  },
  { date: "Wed, 25 Jun", status: "present", time: "08:41 AM", subject: "Computer Networks" },
  { date: "Tue, 24 Jun", status: "absent",  time: null,       subject: "Data Structures"   },
  { date: "Mon, 23 Jun", status: "od",      time: null,       subject: "Operating Systems" },
  { date: "Fri, 20 Jun", status: "present", time: "08:36 AM", subject: "Database Systems"  },
  { date: "Thu, 19 Jun", status: "present", time: "08:44 AM", subject: "Computer Networks" },
  { date: "Wed, 18 Jun", status: "halfd",   time: "08:40 AM", subject: "Data Structures"   },
  { date: "Tue, 17 Jun", status: "present", time: "08:38 AM", subject: "Operating Systems" },
];

const PERMISSIONS = [
  { id: 1, type: "OD",       date: "Mon, 23 Jun", reason: "Inter-college Tech Symposium",          status: "approved", by: "Dr. Rajesh Kumar" },
  { id: 2, type: "Half-Day", date: "Wed, 18 Jun", reason: "Medical appointment",                   status: "approved", by: "Dr. Rajesh Kumar" },
  { id: 3, type: "OD",       date: "Thu, 3 Jul",  reason: "National Level Hackathon — VIT Chennai", status: "pending",  by: null },
  { id: 4, type: "One-Hour", date: "Wed, 2 Jul",  reason: "Bank work — passbook update",            status: "rejected", by: "Dr. Rajesh Kumar" },
];

const NOTIFS = [
  { id: 1, type: "warning",  title: "Late Warning",        body: "You have been late 2 times this month. One more will trigger an automatic warning email to the HOD.", time: "2h ago",  read: false },
  { id: 2, type: "approved", title: "Permission Approved", body: "Your OD request for 23 Jun (Tech Symposium) has been approved by Dr. Rajesh Kumar.",                  time: "1d ago",  read: false },
  { id: 3, type: "info",     title: "Weekly Report Sent",  body: "Your attendance report for the week has been sent to your registered email.",                           time: "3d ago",  read: true  },
  { id: 4, type: "rejected", title: "Permission Rejected", body: "Your One-Hour permission for 2 Jul has been rejected.",                                                 time: "1d ago",  read: true  },
];

const STAFF_STUDENTS = [
  { id: 1,  name: "Priya Sharma",    rollNo: "CS21001", status: "present", time: "08:42 AM" },
  { id: 2,  name: "Arjun Mehta",     rollNo: "CS21002", status: "late",    time: "09:11 AM" },
  { id: 3,  name: "Riya Patel",      rollNo: "CS21003", status: "present", time: "08:39 AM" },
  { id: 4,  name: "Karthik Iyer",    rollNo: "CS21004", status: "absent",  time: null        },
  { id: 5,  name: "Sneha Nair",      rollNo: "CS21005", status: "od",      time: null        },
  { id: 6,  name: "Vikram Reddy",    rollNo: "CS21006", status: "present", time: "08:44 AM" },
  { id: 7,  name: "Ananya Singh",    rollNo: "CS21007", status: "halfd",   time: "08:41 AM" },
  { id: 8,  name: "Rahul Das",       rollNo: "CS21008", status: "present", time: "08:47 AM" },
  { id: 9,  name: "Meera Krishnan",  rollNo: "CS21009", status: "late",    time: "09:08 AM" },
  { id: 10, name: "Devraj Joshi",    rollNo: "CS21010", status: "absent",  time: null        },
  { id: 11, name: "Tanvi Gupta",     rollNo: "CS21011", status: "present", time: "08:40 AM" },
  { id: 12, name: "Aditya Kulkarni", rollNo: "CS21012", status: "present", time: "08:43 AM" },
];

const REPORT_DATA = [
  { day: "Mon", present: 38, late: 3, absent: 3 },
  { day: "Tue", present: 36, late: 4, absent: 4 },
  { day: "Wed", present: 39, late: 2, absent: 3 },
  { day: "Thu", present: 35, late: 5, absent: 4 },
  { day: "Fri", present: 33, late: 4, absent: 7 },
];

const SUSPICIOUS = [
  { id: 1, student: "Karthik Iyer", rollNo: "CS21004", reason: "Location mismatch — 2.3 km from campus",   time: "09:02 AM" },
  { id: 2, student: "Devraj Joshi", rollNo: "CS21010", reason: "Unknown device — not a registered device",  time: "08:58 AM" },
];

const APPROVALS = [
  { id: 1, student: "Priya Sharma", rollNo: "CS21001", type: "OD",       date: "Thu, 3 Jul", reason: "National Hackathon — VIT Chennai"        },
  { id: 2, student: "Arjun Mehta",  rollNo: "CS21002", type: "Half-Day", date: "Fri, 4 Jul", reason: "Dental appointment (morning session)"    },
];

// ─── Status config ──────────────────────────────────────────────────────────

const STATUS_CFG: Record<string, { label: string; bg: string; text: string; dot: string; dbg: string; dt: string }> = {
  present:    { label: "Present",    bg: "bg-emerald-50",  text: "text-emerald-700", dot: "bg-emerald-500", dbg: "dark:bg-emerald-950/40", dt: "dark:text-emerald-400" },
  late:       { label: "Late",       bg: "bg-amber-50",    text: "text-amber-700",   dot: "bg-amber-500",   dbg: "dark:bg-amber-950/40",   dt: "dark:text-amber-400"   },
  absent:     { label: "Absent",     bg: "bg-red-50",      text: "text-red-700",     dot: "bg-red-500",     dbg: "dark:bg-red-950/40",     dt: "dark:text-red-400"     },
  od:         { label: "OD",         bg: "bg-blue-50",     text: "text-blue-700",    dot: "bg-blue-500",    dbg: "dark:bg-blue-950/40",    dt: "dark:text-blue-400"    },
  halfd:      { label: "Half-Day",   bg: "bg-purple-50",   text: "text-purple-700",  dot: "bg-purple-500",  dbg: "dark:bg-purple-950/40",  dt: "dark:text-purple-400"  },
  permission: { label: "Permission", bg: "bg-teal-50",     text: "text-teal-700",    dot: "bg-teal-500",    dbg: "dark:bg-teal-950/40",    dt: "dark:text-teal-400"    },
  pending:    { label: "Pending",    bg: "bg-orange-50",   text: "text-orange-700",  dot: "bg-orange-500",  dbg: "dark:bg-orange-950/40",  dt: "dark:text-orange-400"  },
  approved:   { label: "Approved",   bg: "bg-emerald-50",  text: "text-emerald-700", dot: "bg-emerald-500", dbg: "dark:bg-emerald-950/40", dt: "dark:text-emerald-400" },
  rejected:   { label: "Rejected",   bg: "bg-red-50",      text: "text-red-700",     dot: "bg-red-500",     dbg: "dark:bg-red-950/40",     dt: "dark:text-red-400"     },
};

// ─── Shared UI primitives ───────────────────────────────────────────────────

function StatusChip({ status }: { status: string }) {
  const c = STATUS_CFG[status] ?? STATUS_CFG.absent;
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${c.bg} ${c.text} ${c.dbg} ${c.dt}`}>
      <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${c.dot}`} />
      {c.label}
    </span>
  );
}

function Av({ initials, size = "md" }: { initials: string; size?: "sm" | "md" | "lg" }) {
  const s = { sm: "w-8 h-8 text-xs", md: "w-10 h-10 text-sm", lg: "w-14 h-14 text-lg" }[size];
  return (
    <div className={`${s} rounded-full bg-blue-600 flex items-center justify-center font-bold text-white flex-shrink-0`}>
      {initials}
    </div>
  );
}

function SecHdr({ title, action, onAction }: { title: string; action?: string; onAction?: () => void }) {
  return (
    <div className="flex items-center justify-between mb-2.5">
      <h3 className="text-xs font-bold text-muted-foreground uppercase tracking-widest">{title}</h3>
      {action && <button onClick={onAction} className="text-xs text-blue-600 dark:text-blue-400 font-semibold">{action}</button>}
    </div>
  );
}

function BackBtn({ onBack, label = "Back" }: { onBack: () => void; label?: string }) {
  return (
    <button onClick={onBack} className="flex items-center gap-1 text-blue-600 dark:text-blue-400 font-semibold text-sm">
      <ArrowLeft className="w-4 h-4" />
      {label}
    </button>
  );
}

function StatCard({ label, value, color }: { label: string; value: number | string; color: string }) {
  return (
    <div className="flex-1 bg-card rounded-2xl p-3 flex flex-col gap-0.5 shadow-sm border border-border">
      <span className={`text-xl font-bold ${color}`}>{value}</span>
      <span className="text-xs text-muted-foreground font-medium">{label}</span>
    </div>
  );
}

// ─── SCREEN: Splash ─────────────────────────────────────────────────────────

function SplashScreen() {
  return (
    <motion.div
      className="absolute inset-0 bg-white dark:bg-[#0C1527] flex flex-col items-center justify-center gap-5"
      initial={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.4 }}
    >
      <motion.img
        src={classKeyLogo}
        alt="ClassKey logo"
        className="w-24 h-24 object-contain"
        initial={{ scale: 0.75, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.55, ease: "easeOut" }}
      />
      <motion.div
        className="flex flex-col items-center gap-1"
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.35, duration: 0.4 }}
      >
        <h1 className="text-2xl font-extrabold text-[#0D1B3E] dark:text-white tracking-tight">ClassKey</h1>
        <p className="text-sm text-muted-foreground">Your Presence, Seamlessly Verified.</p>
      </motion.div>
      <motion.div
        className="absolute bottom-14"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.8 }}
      >
        <div className="w-5 h-5 rounded-full border-2 border-blue-600/25 border-t-blue-600 animate-spin" />
      </motion.div>
    </motion.div>
  );
}

// ─── SCREEN: Login ──────────────────────────────────────────────────────────

function LoginScreen({ onLogin }: { onLogin: (role: Role) => void }) {
  return (
    <motion.div
      className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] flex flex-col"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <div className="flex-1 flex flex-col items-center justify-center px-7 gap-6">
        <motion.img
          src={classKeyLogo}
          alt="ClassKey"
          className="w-20 h-20 object-contain"
          initial={{ y: -16, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ duration: 0.5 }}
        />
        <div className="text-center">
          <h1 className="text-3xl font-extrabold text-[#0D1B3E] dark:text-white tracking-tight">ClassKey</h1>
          <p className="text-sm text-muted-foreground mt-1">Your Presence, Seamlessly Verified.</p>
        </div>

        <div className="w-full space-y-3 mt-2">
          <p className="text-center text-xs font-bold text-muted-foreground uppercase tracking-widest">Sign in as</p>

          <motion.button
            className="w-full bg-blue-600 text-white rounded-2xl p-4 flex items-center gap-4 shadow-md shadow-blue-600/25"
            whileTap={{ scale: 0.97 }}
            onClick={() => onLogin("student")}
          >
            <div className="w-11 h-11 rounded-xl bg-white/20 flex items-center justify-center">
              <GraduationCap className="w-6 h-6 text-white" />
            </div>
            <div className="text-left flex-1">
              <div className="font-bold text-base">Student</div>
              <div className="text-blue-100 text-xs mt-0.5">Mark attendance · View history</div>
            </div>
            <ChevronRight className="w-5 h-5 text-white/50" />
          </motion.button>

          <motion.button
            className="w-full bg-card text-foreground rounded-2xl p-4 flex items-center gap-4 shadow-sm border border-border"
            whileTap={{ scale: 0.97 }}
            onClick={() => onLogin("staff")}
          >
            <div className="w-11 h-11 rounded-xl bg-blue-50 dark:bg-blue-950/30 flex items-center justify-center">
              <ClipboardList className="w-6 h-6 text-blue-600 dark:text-blue-400" />
            </div>
            <div className="text-left flex-1">
              <div className="font-bold text-base">Staff / Admin</div>
              <div className="text-muted-foreground text-xs mt-0.5">Manage attendance · Reports</div>
            </div>
            <ChevronRight className="w-5 h-5 text-muted-foreground" />
          </motion.button>
        </div>
      </div>

      <div className="pb-8 px-7 flex flex-col items-center gap-3">
        <div className="flex items-center gap-5 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5"><Shield className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />Biometric</span>
          <span className="flex items-center gap-1.5"><MapPin className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />Location</span>
          <span className="flex items-center gap-1.5"><Smartphone className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />Trusted Device</span>
        </div>
        <p className="text-xs text-muted-foreground/50">ClassKey Enterprise · v2.4.1</p>
      </div>
    </motion.div>
  );
}

// ─── SCREEN: Student Home ───────────────────────────────────────────────────

function StudentHome({ onMarkAttendance, onTab }: {
  onMarkAttendance: () => void;
  onTab: (t: StudentScreen) => void;
}) {
  const week = [
    { d: "M", s: "present" }, { d: "T", s: "absent" }, { d: "W", s: "present" },
    { d: "T", s: "late" },    { d: "F", s: "present" }, { d: "S", s: "present" },
    { d: "S", s: null },
  ];

  return (
    <div className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] overflow-y-auto" style={{ scrollbarWidth: "none" }}>
      <div className="px-5 pt-4 pb-24 space-y-5">

        {/* Greeting row */}
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs text-muted-foreground font-medium">Good morning,</p>
            <h2 className="text-lg font-bold text-foreground">Priya Sharma 👋</h2>
          </div>
          <div className="flex items-center gap-2.5">
            <button onClick={() => onTab("notifications")} className="relative w-10 h-10 rounded-full bg-card border border-border flex items-center justify-center shadow-sm">
              <Bell className="w-4.5 h-4.5 text-foreground" style={{ width: "1.1rem", height: "1.1rem" }} />
              <span className="absolute top-2 right-2 w-2 h-2 bg-red-500 rounded-full border-2 border-card" />
            </button>
            <Av initials="PS" size="md" />
          </div>
        </div>

        {/* Today marked — blue card */}
        <div className="bg-blue-600 rounded-2xl p-4 text-white shadow-lg shadow-blue-600/20">
          <div className="flex items-start justify-between gap-3">
            <div className="flex-1 min-w-0">
              <p className="text-blue-200 text-xs font-medium">Current Class</p>
              <h3 className="text-base font-bold mt-0.5 leading-tight">{TODAY_CLASS.subject}</h3>
              <p className="text-blue-200 text-xs mt-0.5">{TODAY_CLASS.code} · {TODAY_CLASS.room}</p>
            </div>
            <div className="bg-white/15 rounded-xl px-3 py-2 text-right flex-shrink-0">
              <p className="text-blue-200 text-xs">Time</p>
              <p className="text-sm font-bold">{TODAY_CLASS.time.split("–")[0].trim()}</p>
            </div>
          </div>
          <div className="mt-3 flex items-center gap-2">
            <StatusChip status="present" />
            <span className="text-blue-200 text-xs">Marked · 08:42 AM</span>
          </div>
        </div>

        {/* Upcoming class + mark button */}
        <div className="bg-card rounded-2xl p-4 border border-border shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <div>
              <p className="text-sm font-bold text-foreground">Upcoming — 11:00 AM</p>
              <p className="text-xs text-muted-foreground">Operating Systems · CS302 · LH-101</p>
            </div>
            <Clock className="w-4 h-4 text-muted-foreground" />
          </div>
          <motion.button
            className="w-full bg-blue-600 text-white rounded-xl py-3 flex items-center justify-center gap-2 font-bold text-sm shadow-sm"
            whileTap={{ scale: 0.97 }}
            onClick={onMarkAttendance}
          >
            <Fingerprint className="w-4 h-4" />
            Mark Attendance
          </motion.button>
          <p className="text-center text-xs text-muted-foreground mt-2">Opens when class begins · Grace: 10 min</p>
        </div>

        {/* This week strip */}
        <div>
          <SecHdr title="This Week" action="Full History" onAction={() => onTab("history")} />
          <div className="bg-card rounded-2xl p-4 border border-border shadow-sm">
            <div className="flex justify-between items-center">
              {week.map(({ d, s }, i) => (
                <div key={i} className="flex flex-col items-center gap-1.5">
                  <span className="text-xs text-muted-foreground font-medium">{d}</span>
                  <div className={`w-7 h-7 rounded-full flex items-center justify-center transition-all ${
                    s === "present" ? "bg-emerald-500" :
                    s === "late"    ? "bg-amber-500"   :
                    s === "absent"  ? "bg-red-500"     :
                    "bg-muted opacity-40"
                  }`}>
                    {s === "present" && <Check className="w-3.5 h-3.5 text-white" strokeWidth={3} />}
                    {s === "late"    && <Clock className="w-3 h-3 text-white" />}
                    {s === "absent"  && <X className="w-3.5 h-3.5 text-white" strokeWidth={3} />}
                  </div>
                </div>
              ))}
            </div>
            <div className="mt-3 pt-3 border-t border-border flex items-center justify-between">
              <div className="flex items-center gap-3 text-xs text-muted-foreground">
                {[["bg-emerald-500", "Present"], ["bg-amber-500", "Late"], ["bg-red-500", "Absent"]].map(([dot, lbl]) => (
                  <span key={lbl} className="flex items-center gap-1"><span className={`w-2 h-2 rounded-full ${dot}`} />{lbl}</span>
                ))}
              </div>
              <span className="text-xs font-bold text-emerald-600 dark:text-emerald-400">84%</span>
            </div>
          </div>
        </div>

        {/* Quick actions */}
        <div>
          <SecHdr title="Quick Actions" />
          <div className="grid grid-cols-3 gap-3">
            {[
              { Icon: History,  label: "History",     cl: "text-blue-600 dark:text-blue-400",     bg: "bg-blue-50 dark:bg-blue-950/30",     go: () => onTab("history") },
              { Icon: FileText, label: "Permissions",  cl: "text-purple-600 dark:text-purple-400", bg: "bg-purple-50 dark:bg-purple-950/30", go: () => onTab("permissions") },
              { Icon: QrCode,   label: "QR Assist",   cl: "text-teal-600 dark:text-teal-400",     bg: "bg-teal-50 dark:bg-teal-950/30",     go: () => {} },
            ].map(({ Icon, label, cl, bg, go }) => (
              <motion.button key={label} onClick={go} className="bg-card border border-border rounded-2xl p-3 flex flex-col items-center gap-2 shadow-sm" whileTap={{ scale: 0.94 }}>
                <div className={`w-10 h-10 rounded-xl ${bg} flex items-center justify-center`}>
                  <Icon className={`w-5 h-5 ${cl}`} />
                </div>
                <span className="text-xs font-semibold text-foreground">{label}</span>
              </motion.button>
            ))}
          </div>
        </div>

        {/* Recent */}
        <div>
          <SecHdr title="Recent" action="See All" onAction={() => onTab("history")} />
          <div className="bg-card rounded-2xl border border-border shadow-sm overflow-hidden">
            {HISTORY.slice(0, 4).map((item, i) => (
              <div key={i} className={`flex items-center gap-3 px-4 py-3 ${i < 3 ? "border-b border-border" : ""}`}>
                <div className={`w-2 h-2 rounded-full flex-shrink-0 ${
                  STATUS_CFG[item.status]?.dot ?? "bg-slate-400"
                }`} />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-foreground truncate">{item.subject}</p>
                  <p className="text-xs text-muted-foreground">{item.date}</p>
                </div>
                <div className="flex flex-col items-end gap-0.5">
                  <StatusChip status={item.status} />
                  {item.time && <span className="text-xs text-muted-foreground" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{item.time}</span>}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── OVERLAY: Mark Attendance ───────────────────────────────────────────────

function MarkAttendanceScreen({ onClose, onSuccess }: {
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [scanning, setScanning] = useState(false);
  const [grace, setGrace] = useState(TODAY_CLASS.graceLeft);

  useEffect(() => {
    if (grace <= 0) return;
    const t = setInterval(() => setGrace((g) => Math.max(0, g - 1)), 1000);
    return () => clearInterval(t);
  }, []);

  const mins = Math.floor(grace / 60);
  const secs = grace % 60;
  const R = 44;
  const C = 2 * Math.PI * R;
  const offset = C * (1 - grace / TODAY_CLASS.graceLeft);
  const strokeColor = grace < 120 ? "#ef4444" : "#f59e0b";
  const textColor   = grace < 120 ? "#ef4444" : "#d97706";

  const handleBiometric = async () => {
    setScanning(true);
    await new Promise((r) => setTimeout(r, 2200));
    setScanning(false);
    onSuccess();
  };

  return (
    <motion.div
      className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] flex flex-col z-10"
      initial={{ y: "100%" }}
      animate={{ y: 0 }}
      exit={{ y: "100%" }}
      transition={{ type: "spring", damping: 28, stiffness: 300 }}
    >
      <div className="px-5 pt-4 pb-3 flex items-center border-b border-border">
        <BackBtn onBack={onClose} />
        <h2 className="flex-1 text-center font-bold text-foreground text-base mr-12">Mark Attendance</h2>
      </div>

      <div className="flex-1 overflow-y-auto px-5 pt-4 pb-8 space-y-5" style={{ scrollbarWidth: "none" }}>
        {/* Class card */}
        <div className="bg-card rounded-2xl p-4 border border-border shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs text-muted-foreground">Class</p>
              <h3 className="font-bold text-base text-foreground mt-0.5">{TODAY_CLASS.subject}</h3>
              <p className="text-xs text-muted-foreground mt-0.5">{TODAY_CLASS.code} · Room {TODAY_CLASS.room}</p>
            </div>
            <div className="text-right">
              <p className="text-xs text-muted-foreground">Session</p>
              <p className="text-sm font-bold text-foreground mt-0.5">{TODAY_CLASS.time}</p>
            </div>
          </div>
        </div>

        {/* Verification badges */}
        <div className="grid grid-cols-2 gap-3">
          <div className="bg-emerald-50 dark:bg-emerald-950/30 rounded-xl p-3 flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-emerald-100 dark:bg-emerald-900/40 flex items-center justify-center">
              <MapPin className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
            </div>
            <div>
              <p className="text-xs font-bold text-emerald-700 dark:text-emerald-400">Location</p>
              <p className="text-xs text-emerald-600/70 dark:text-emerald-500">On Campus ✓</p>
            </div>
          </div>
          <div className="bg-emerald-50 dark:bg-emerald-950/30 rounded-xl p-3 flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-emerald-100 dark:bg-emerald-900/40 flex items-center justify-center">
              <Smartphone className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
            </div>
            <div>
              <p className="text-xs font-bold text-emerald-700 dark:text-emerald-400">Device</p>
              <p className="text-xs text-emerald-600/70 dark:text-emerald-500">Trusted ✓</p>
            </div>
          </div>
        </div>

        {/* Grace countdown */}
        <div className="flex flex-col items-center gap-2 py-2">
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Grace Time Remaining</p>
          <svg width="128" height="128" viewBox="0 0 120 120">
            <circle cx="60" cy="60" r={R} fill="none" stroke="#fef3c7" strokeWidth="6" className="dark:opacity-20" />
            <circle
              cx="60" cy="60" r={R} fill="none"
              stroke={strokeColor}
              strokeWidth="6"
              strokeLinecap="round"
              strokeDasharray={C}
              strokeDashoffset={offset}
              transform="rotate(-90 60 60)"
              style={{ transition: "stroke-dashoffset 0.9s linear, stroke 0.5s ease" }}
            />
            <text
              x="60" y="56" textAnchor="middle"
              fontSize="22" fontWeight="700"
              fill={textColor}
              style={{ fontFamily: "'JetBrains Mono', monospace" }}
            >
              {mins}:{String(secs).padStart(2, "0")}
            </text>
            <text x="60" y="74" textAnchor="middle" fontSize="10" fill="#9ca3af">remaining</text>
          </svg>
        </div>

        {/* Biometric button */}
        <div className="flex flex-col items-center gap-4 py-2">
          <p className="text-sm text-muted-foreground">
            {scanning ? "Verifying biometric…" : "Tap to verify your biometric"}
          </p>

          <div className="relative flex items-center justify-center h-36 w-36">
            {!scanning && (
              <>
                <motion.div
                  className="absolute inset-0 rounded-full bg-blue-500/10"
                  animate={{ scale: [1, 1.4, 1], opacity: [0.6, 0, 0.6] }}
                  transition={{ repeat: Infinity, duration: 2.4, ease: "easeInOut" }}
                />
                <motion.div
                  className="absolute w-28 h-28 rounded-full bg-blue-500/15"
                  animate={{ scale: [1, 1.22, 1], opacity: [0.8, 0.15, 0.8] }}
                  transition={{ repeat: Infinity, duration: 2.4, ease: "easeInOut", delay: 0.5 }}
                />
              </>
            )}
            <motion.button
              className="relative w-24 h-24 rounded-full bg-blue-600 flex items-center justify-center shadow-xl shadow-blue-600/35 z-10"
              whileTap={{ scale: 0.92 }}
              onClick={handleBiometric}
              disabled={scanning}
            >
              {scanning ? (
                <motion.div animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 1, ease: "linear" }}>
                  <RefreshCw className="w-10 h-10 text-white/80" />
                </motion.div>
              ) : (
                <Fingerprint className="w-12 h-12 text-white" />
              )}
            </motion.button>
          </div>

          {scanning && (
            <motion.p
              className="text-xs font-semibold text-blue-600 dark:text-blue-400"
              animate={{ opacity: [1, 0.3, 1] }}
              transition={{ repeat: Infinity, duration: 1.2 }}
            >
              Scanning fingerprint…
            </motion.p>
          )}
        </div>
      </div>
    </motion.div>
  );
}

// ─── OVERLAY: Attendance Success ────────────────────────────────────────────

function AttendanceSuccessScreen({ onClose }: { onClose: () => void }) {
  return (
    <motion.div
      className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] flex flex-col items-center justify-center gap-6 px-7 z-10"
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.3 }}
    >
      <motion.div
        className="w-24 h-24 rounded-full bg-emerald-500 flex items-center justify-center shadow-xl shadow-emerald-500/30"
        initial={{ scale: 0.4, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", damping: 14, stiffness: 300, delay: 0.1 }}
      >
        <Check className="w-12 h-12 text-white" strokeWidth={3} />
      </motion.div>

      <motion.div
        className="text-center"
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.28, duration: 0.38 }}
      >
        <h2 className="text-2xl font-extrabold text-foreground">Attendance Marked!</h2>
        <p className="text-muted-foreground text-sm mt-1">
          You are marked <span className="text-emerald-600 dark:text-emerald-400 font-bold">Present</span>
        </p>
      </motion.div>

      <motion.div
        className="w-full bg-card rounded-2xl border border-border p-4 space-y-3.5 shadow-sm"
        initial={{ opacity: 0, y: 18 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.38, duration: 0.38 }}
      >
        {[
          { label: "Class",  value: TODAY_CLASS.subject, hi: false },
          { label: "Room",   value: TODAY_CLASS.room,    hi: false },
          { label: "Time",   value: "09:03 AM",          hi: false },
          { label: "Status", value: "Present",            hi: true  },
        ].map(({ label, value, hi }) => (
          <div key={label} className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">{label}</span>
            <span className={`text-sm font-bold ${hi ? "text-emerald-600 dark:text-emerald-400" : "text-foreground"}`}>{value}</span>
          </div>
        ))}
      </motion.div>

      <motion.button
        className="w-full bg-blue-600 text-white rounded-xl py-3.5 font-bold text-sm shadow-sm"
        whileTap={{ scale: 0.97 }}
        onClick={onClose}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.48 }}
      >
        Back to Home
      </motion.button>
    </motion.div>
  );
}

// ─── SCREEN: History ────────────────────────────────────────────────────────

function HistoryScreen() {
  const [filter, setFilter] = useState("all");
  const filters = ["all", "present", "late", "absent", "od", "halfd"];
  const filtered = filter === "all" ? HISTORY : HISTORY.filter((h) => h.status === filter);

  return (
    <div className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] overflow-y-auto" style={{ scrollbarWidth: "none" }}>
      <div className="px-5 pt-4 pb-24 space-y-4">
        <h2 className="text-xl font-extrabold text-foreground">Attendance History</h2>

        <div className="flex gap-2">
          <StatCard label="Present" value={7} color="text-emerald-600 dark:text-emerald-400" />
          <StatCard label="Late"    value={1} color="text-amber-600 dark:text-amber-400" />
          <StatCard label="Absent"  value={1} color="text-red-600 dark:text-red-400" />
          <StatCard label="OD/HD"   value={2} color="text-blue-600 dark:text-blue-400" />
        </div>

        {/* Progress bar */}
        <div className="bg-card rounded-2xl p-4 border border-border shadow-sm">
          <div className="flex justify-between items-center mb-2.5">
            <span className="text-sm font-semibold text-foreground">Overall Attendance</span>
            <span className="text-base font-extrabold text-emerald-600 dark:text-emerald-400">84%</span>
          </div>
          <div className="h-2 bg-muted rounded-full overflow-hidden">
            <motion.div
              className="h-full bg-emerald-500 rounded-full"
              initial={{ width: 0 }}
              animate={{ width: "84%" }}
              transition={{ duration: 0.9, ease: "easeOut", delay: 0.2 }}
            />
          </div>
          <p className="text-xs text-muted-foreground mt-2">Minimum required: 75% · You are safe ✓</p>
        </div>

        {/* Filter chips */}
        <div className="flex gap-2 overflow-x-auto pb-1" style={{ scrollbarWidth: "none" }}>
          {filters.map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`flex-shrink-0 px-3.5 py-1.5 rounded-full text-xs font-bold capitalize transition-all ${
                filter === f ? "bg-blue-600 text-white" : "bg-card border border-border text-muted-foreground"
              }`}
            >
              {f === "all" ? "All" : STATUS_CFG[f]?.label ?? f}
            </button>
          ))}
        </div>

        <div className="bg-card rounded-2xl border border-border shadow-sm overflow-hidden">
          {filtered.length === 0 ? (
            <div className="py-12 text-center text-muted-foreground text-sm">No records for this filter</div>
          ) : filtered.map((item, i) => (
            <div key={i} className={`flex items-center gap-3 px-4 py-3.5 ${i < filtered.length - 1 ? "border-b border-border" : ""}`}>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-foreground truncate">{item.subject}</p>
                <p className="text-xs text-muted-foreground">{item.date}</p>
              </div>
              <div className="flex flex-col items-end gap-0.5">
                <StatusChip status={item.status} />
                {item.time && <span className="text-xs text-muted-foreground" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{item.time}</span>}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── SCREEN: Permissions ────────────────────────────────────────────────────

function PermissionsScreen({ onNewRequest }: { onNewRequest: () => void }) {
  const [filter, setFilter] = useState("all");
  const filters = ["all", "pending", "approved", "rejected"];
  const filtered = filter === "all" ? PERMISSIONS : PERMISSIONS.filter((p) => p.status === filter);

  return (
    <div className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] overflow-y-auto" style={{ scrollbarWidth: "none" }}>
      <div className="px-5 pt-4 pb-24 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-extrabold text-foreground">Permissions</h2>
          <motion.button
            className="w-9 h-9 bg-blue-600 rounded-full flex items-center justify-center shadow-sm"
            whileTap={{ scale: 0.9 }}
            onClick={onNewRequest}
          >
            <Plus className="w-5 h-5 text-white" />
          </motion.button>
        </div>

        <div className="flex gap-2">
          {filters.map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`flex-1 py-1.5 rounded-full text-xs font-bold capitalize transition-all ${
                filter === f ? "bg-blue-600 text-white" : "bg-card border border-border text-muted-foreground"
              }`}
            >
              {f.charAt(0).toUpperCase() + f.slice(1)}
            </button>
          ))}
        </div>

        <div className="space-y-3">
          {filtered.map((req) => (
            <div key={req.id} className="bg-card rounded-2xl p-4 border border-border shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1.5">
                    <span className="text-xs font-bold text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-950/30 px-2 py-0.5 rounded-lg">{req.type}</span>
                    <span className="text-xs text-muted-foreground">{req.date}</span>
                  </div>
                  <p className="text-sm font-semibold text-foreground">{req.reason}</p>
                  {req.by && (
                    <p className="text-xs text-muted-foreground mt-1.5">
                      {req.status === "approved" ? "✓ Approved" : req.status === "rejected" ? "✗ Rejected" : "Pending"} by {req.by}
                    </p>
                  )}
                </div>
                <StatusChip status={req.status} />
              </div>
            </div>
          ))}
          {filtered.length === 0 && (
            <div className="py-12 text-center text-muted-foreground text-sm">No {filter} requests</div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── OVERLAY: New Permission ────────────────────────────────────────────────

function NewPermissionForm({ onClose }: { onClose: () => void }) {
  const [type, setType] = useState("OD");
  const [reason, setReason] = useState("");

  return (
    <motion.div
      className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] flex flex-col z-10"
      initial={{ y: "100%" }}
      animate={{ y: 0 }}
      exit={{ y: "100%" }}
      transition={{ type: "spring", damping: 28, stiffness: 300 }}
    >
      <div className="px-5 pt-4 pb-3 flex items-center border-b border-border">
        <BackBtn onBack={onClose} label="Cancel" />
        <h2 className="flex-1 text-center font-bold text-foreground text-base mr-14">New Request</h2>
      </div>

      <div className="flex-1 overflow-y-auto px-5 pt-5 pb-8 space-y-5" style={{ scrollbarWidth: "none" }}>
        <div>
          <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest block mb-2.5">Permission Type</label>
          <div className="flex gap-2">
            {["OD", "Half-Day", "One-Hour"].map((t) => (
              <button
                key={t}
                onClick={() => setType(t)}
                className={`flex-1 py-2.5 rounded-xl text-sm font-bold border-2 transition-all ${
                  type === t ? "bg-blue-600 text-white border-blue-600" : "bg-card border-border text-muted-foreground"
                }`}
              >
                {t}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest block mb-2.5">Date</label>
          <div className="bg-card border border-border rounded-xl px-4 py-3 flex items-center gap-3">
            <Calendar className="w-4 h-4 text-muted-foreground" />
            <span className="text-sm text-foreground font-semibold">Thursday, 3 July 2025</span>
          </div>
        </div>

        <div>
          <label className="text-xs font-bold text-muted-foreground uppercase tracking-widest block mb-2.5">Reason</label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={4}
            placeholder="Describe the reason for your request…"
            className="w-full bg-card border border-border rounded-xl px-4 py-3 text-sm text-foreground placeholder:text-muted-foreground resize-none focus:outline-none focus:ring-2 focus:ring-blue-600/30"
          />
        </div>

        <div className="bg-card border-2 border-dashed border-border rounded-xl p-4 flex flex-col items-center gap-2">
          <FileText className="w-6 h-6 text-muted-foreground" />
          <p className="text-sm text-muted-foreground">Supporting document <span className="text-xs">(optional)</span></p>
          <button className="text-xs text-blue-600 dark:text-blue-400 font-semibold">Browse Files</button>
        </div>

        <motion.button
          className="w-full bg-blue-600 text-white rounded-xl py-3.5 font-bold text-sm flex items-center justify-center gap-2 shadow-sm"
          whileTap={{ scale: 0.97 }}
          onClick={onClose}
        >
          <Send className="w-4 h-4" />
          Submit Request
        </motion.button>
      </div>
    </motion.div>
  );
}

// ─── SCREEN: Notifications ──────────────────────────────────────────────────

function NotificationsScreen() {
  const icons: Record<string, { Icon: LucideIcon; cl: string; bg: string }> = {
    warning:  { Icon: AlertTriangle, cl: "text-amber-600 dark:text-amber-400",   bg: "bg-amber-50 dark:bg-amber-950/30"   },
    approved: { Icon: CheckCircle2,  cl: "text-emerald-600 dark:text-emerald-400", bg: "bg-emerald-50 dark:bg-emerald-950/30" },
    info:     { Icon: Bell,          cl: "text-blue-600 dark:text-blue-400",     bg: "bg-blue-50 dark:bg-blue-950/30"     },
    rejected: { Icon: XCircle,       cl: "text-red-600 dark:text-red-400",       bg: "bg-red-50 dark:bg-red-950/30"       },
  };

  return (
    <div className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] overflow-y-auto" style={{ scrollbarWidth: "none" }}>
      <div className="px-5 pt-4 pb-24 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-extrabold text-foreground">Notifications</h2>
          <button className="text-xs text-blue-600 dark:text-blue-400 font-semibold">Mark all read</button>
        </div>

        <div className="space-y-2.5">
          {NOTIFS.map((n) => {
            const cfg = icons[n.type] ?? icons.info;
            const { Icon } = cfg;
            return (
              <div
                key={n.id}
                className={`bg-card rounded-2xl p-4 border shadow-sm flex gap-3 ${
                  !n.read ? "border-blue-200 dark:border-blue-800/40" : "border-border"
                }`}
              >
                <div className={`w-9 h-9 rounded-xl ${cfg.bg} flex items-center justify-center flex-shrink-0`}>
                  <Icon className={`w-5 h-5 ${cfg.cl}`} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-sm font-bold text-foreground">{n.title}</p>
                    {!n.read && <span className="w-2 h-2 rounded-full bg-blue-600 flex-shrink-0 mt-1" />}
                  </div>
                  <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">{n.body}</p>
                  <p className="text-xs text-muted-foreground/50 mt-1.5">{n.time}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// ─── SCREEN: Student Profile ────────────────────────────────────────────────

function ProfileScreen({ isDark, onToggleDark, onLogout }: {
  isDark: boolean;
  onToggleDark: () => void;
  onLogout: () => void;
}) {
  const [bio, setBio] = useState(true);
  const [notif, setNotif] = useState(true);
  const [loc, setLoc] = useState(true);

  const rows = [
    { label: "Biometric Auth",      Icon: Fingerprint, val: bio,   set: setBio },
    { label: "Push Notifications",  Icon: Bell,        val: notif, set: setNotif },
    { label: "Dark Mode",           Icon: isDark ? Moon : Sun, val: isDark, set: onToggleDark },
    { label: "Location Services",   Icon: MapPin,      val: loc,   set: setLoc },
  ];

  return (
    <div className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] overflow-y-auto" style={{ scrollbarWidth: "none" }}>
      <div className="px-5 pt-4 pb-24 space-y-5">
        <h2 className="text-xl font-extrabold text-foreground">Profile</h2>

        <div className="bg-card rounded-2xl p-4 border border-border shadow-sm flex items-center gap-4">
          <Av initials="PS" size="lg" />
          <div>
            <h3 className="font-bold text-foreground text-base">{STUDENT_USER.name}</h3>
            <p className="text-xs text-muted-foreground">{STUDENT_USER.rollNo}</p>
            <p className="text-xs text-muted-foreground">{STUDENT_USER.dept} · {STUDENT_USER.year}</p>
          </div>
        </div>

        <div>
          <SecHdr title="Settings" />
          <div className="bg-card rounded-2xl border border-border shadow-sm overflow-hidden">
            {rows.map(({ label, Icon, val, set }, i) => (
              <div key={label} className={`flex items-center gap-3 px-4 py-3.5 ${i < rows.length - 1 ? "border-b border-border" : ""}`}>
                <div className="w-8 h-8 rounded-lg bg-muted flex items-center justify-center">
                  <Icon className="w-4 h-4 text-muted-foreground" />
                </div>
                <span className="flex-1 text-sm font-semibold text-foreground">{label}</span>
                <button
                  onClick={() => typeof set === "function" && (set as () => void)()}
                  className={`w-11 h-6 rounded-full relative transition-colors duration-200 ${val ? "bg-blue-600" : "bg-muted"}`}
                >
                  <motion.div
                    className="absolute top-0.5 bottom-0.5 w-5 rounded-full bg-white shadow"
                    animate={{ left: val ? "calc(100% - 1.375rem)" : "2px" }}
                    transition={{ type: "spring", stiffness: 500, damping: 32 }}
                  />
                </button>
              </div>
            ))}
          </div>
        </div>

        <motion.button
          className="w-full bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 rounded-2xl py-3.5 flex items-center justify-center gap-2 font-bold text-sm border border-red-100 dark:border-red-900/40"
          whileTap={{ scale: 0.97 }}
          onClick={onLogout}
        >
          <LogOut className="w-4 h-4" />
          Sign Out
        </motion.button>
      </div>
    </div>
  );
}

// ─── SCREEN: Staff Dashboard ────────────────────────────────────────────────

function StaffDashboard({ onSuspicious, onApprovals, onTab }: {
  onSuspicious: () => void;
  onApprovals: () => void;
  onTab: (t: StaffScreen) => void;
}) {
  return (
    <div className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] overflow-y-auto" style={{ scrollbarWidth: "none" }}>
      <div className="px-5 pt-4 pb-24 space-y-5">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs text-muted-foreground font-medium">Good morning,</p>
            <h2 className="text-lg font-bold text-foreground">Dr. Rajesh Kumar 👋</h2>
          </div>
          <Av initials="RK" size="md" />
        </div>

        {/* Active class banner */}
        <div className="bg-blue-600 rounded-2xl p-4 text-white shadow-lg shadow-blue-600/20">
          <div className="flex items-start justify-between gap-3">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
                <p className="text-blue-200 text-xs font-medium">Live · Thu, 3 July 2025</p>
              </div>
              <h3 className="text-base font-bold leading-tight">{TODAY_CLASS.subject}</h3>
              <p className="text-blue-200 text-xs mt-0.5">{TODAY_CLASS.code} · {TODAY_CLASS.room}</p>
            </div>
            <div className="bg-white/15 rounded-xl px-3 py-2 text-right flex-shrink-0">
              <p className="text-blue-200 text-xs">Grace</p>
              <p className="text-sm font-bold">7 min</p>
            </div>
          </div>
        </div>

        {/* Stats */}
        <div>
          <SecHdr title="Today's Summary" />
          <div className="flex gap-2">
            <StatCard label="Present" value={32} color="text-emerald-600 dark:text-emerald-400" />
            <StatCard label="Late"    value={4}  color="text-amber-600 dark:text-amber-400" />
            <StatCard label="Absent"  value={6}  color="text-red-600 dark:text-red-400" />
            <StatCard label="OD"      value={2}  color="text-blue-600 dark:text-blue-400" />
          </div>
        </div>

        {/* Quick actions */}
        <div>
          <SecHdr title="Quick Actions" />
          <div className="grid grid-cols-2 gap-3">
            {[
              { Icon: Users,     label: "Student List", sub: "Live attendance",         cl: "text-blue-600 dark:text-blue-400",     bg: "bg-blue-50 dark:bg-blue-950/30",     go: () => onTab("students") },
              { Icon: QrCode,    label: "QR Scanner",   sub: "Manual mark",             cl: "text-teal-600 dark:text-teal-400",     bg: "bg-teal-50 dark:bg-teal-950/30",     go: () => onTab("qr") },
              { Icon: BarChart3, label: "Reports",      sub: "Download & email",        cl: "text-purple-600 dark:text-purple-400", bg: "bg-purple-50 dark:bg-purple-950/30", go: () => onTab("reports") },
              { Icon: CheckCheck, label: "Approvals",   sub: `${APPROVALS.length} pending`, cl: "text-orange-600 dark:text-orange-400", bg: "bg-orange-50 dark:bg-orange-950/30", go: onApprovals },
            ].map(({ Icon, label, sub, cl, bg, go }) => (
              <motion.button
                key={label}
                onClick={go}
                className="bg-card border border-border rounded-2xl p-3.5 flex items-center gap-3 shadow-sm text-left"
                whileTap={{ scale: 0.95 }}
              >
                <div className={`w-10 h-10 rounded-xl ${bg} flex items-center justify-center flex-shrink-0`}>
                  <Icon className={`w-5 h-5 ${cl}`} />
                </div>
                <div>
                  <p className="text-sm font-bold text-foreground">{label}</p>
                  <p className="text-xs text-muted-foreground">{sub}</p>
                </div>
              </motion.button>
            ))}
          </div>
        </div>

        {/* Suspicious attempts banner */}
        {SUSPICIOUS.length > 0 && (
          <div>
            <SecHdr title="Suspicious Attempts" action="Review" onAction={onSuspicious} />
            <div className="bg-amber-50 dark:bg-amber-950/20 rounded-2xl border border-amber-200 dark:border-amber-800/30 overflow-hidden">
              {SUSPICIOUS.map((s, i) => (
                <div key={s.id} className={`px-4 py-3 flex items-start gap-3 ${i < SUSPICIOUS.length - 1 ? "border-b border-amber-200 dark:border-amber-800/30" : ""}`}>
                  <AlertTriangle className="w-4 h-4 text-amber-600 dark:text-amber-400 flex-shrink-0 mt-0.5" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-bold text-amber-800 dark:text-amber-300">
                      {s.student} <span className="font-normal text-xs text-amber-600 dark:text-amber-400">· {s.rollNo}</span>
                    </p>
                    <p className="text-xs text-amber-700 dark:text-amber-400 mt-0.5">{s.reason}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Live list preview */}
        <div>
          <SecHdr title="Live Attendance" action="Full List" onAction={() => onTab("students")} />
          <div className="bg-card rounded-2xl border border-border shadow-sm overflow-hidden">
            {STAFF_STUDENTS.slice(0, 5).map((s, i) => (
              <div key={s.id} className={`flex items-center gap-3 px-4 py-3 ${i < 4 ? "border-b border-border" : ""}`}>
                <Av initials={s.name.split(" ").map(n => n[0]).join("").slice(0, 2)} size="sm" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-foreground truncate">{s.name}</p>
                  <p className="text-xs text-muted-foreground" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{s.rollNo}</p>
                </div>
                <div className="flex flex-col items-end gap-0.5">
                  <StatusChip status={s.status} />
                  {s.time && <span className="text-xs text-muted-foreground" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{s.time}</span>}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── SCREEN: Student List ───────────────────────────────────────────────────

function StudentListScreen() {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("all");
  const filters = ["all", "present", "late", "absent", "od"];

  const counts = { all: 12, present: 8, late: 2, absent: 2, od: 1 };

  const filtered = STAFF_STUDENTS.filter((s) => {
    const mS = s.name.toLowerCase().includes(search.toLowerCase()) || s.rollNo.toLowerCase().includes(search.toLowerCase());
    const mF = filter === "all" || s.status === filter || (filter === "od" && (s.status === "od" || s.status === "halfd"));
    return mS && mF;
  });

  return (
    <div className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] flex flex-col">
      <div className="px-5 pt-4 pb-3 space-y-3 flex-shrink-0">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-extrabold text-foreground">Student List</h2>
          <span className="text-xs text-muted-foreground font-medium">{STAFF_STUDENTS.length} students</span>
        </div>
        <div className="bg-card border border-border rounded-xl flex items-center gap-2 px-3 py-2.5 shadow-sm">
          <Search className="w-4 h-4 text-muted-foreground flex-shrink-0" />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search by name or roll number…"
            className="flex-1 bg-transparent text-sm text-foreground placeholder:text-muted-foreground focus:outline-none"
          />
          {search && <button onClick={() => setSearch("")}><X className="w-4 h-4 text-muted-foreground" /></button>}
        </div>
        <div className="flex gap-2 overflow-x-auto" style={{ scrollbarWidth: "none" }}>
          {filters.map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`flex-shrink-0 px-3 py-1.5 rounded-full text-xs font-bold transition-all flex items-center gap-1.5 ${
                filter === f ? "bg-blue-600 text-white" : "bg-card border border-border text-muted-foreground"
              }`}
            >
              {f === "all" ? "All" : STATUS_CFG[f]?.label ?? f}
              <span className={`rounded-full px-1.5 py-0 text-xs leading-4 ${filter === f ? "bg-white/20" : "bg-muted"}`}>
                {counts[f as keyof typeof counts] ?? 0}
              </span>
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-5 pb-24" style={{ scrollbarWidth: "none" }}>
        <div className="bg-card rounded-2xl border border-border shadow-sm overflow-hidden">
          {filtered.length === 0 ? (
            <div className="py-10 text-center text-muted-foreground text-sm">No students found</div>
          ) : filtered.map((s, i) => (
            <div key={s.id} className={`flex items-center gap-3 px-4 py-3.5 ${i < filtered.length - 1 ? "border-b border-border" : ""}`}>
              <Av initials={s.name.split(" ").map(n => n[0]).join("").slice(0, 2)} size="sm" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-foreground truncate">{s.name}</p>
                <p className="text-xs text-muted-foreground" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{s.rollNo}</p>
              </div>
              <div className="flex flex-col items-end gap-0.5">
                <StatusChip status={s.status} />
                {s.time && <span className="text-xs text-muted-foreground" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{s.time}</span>}
              </div>
              <button className="w-7 h-7 rounded-full flex items-center justify-center ml-1 text-muted-foreground">
                <MoreHorizontal className="w-4 h-4" />
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── SCREEN: Reports ────────────────────────────────────────────────────────

function ReportsScreen() {
  const [range, setRange] = useState("week");

  return (
    <div className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] overflow-y-auto" style={{ scrollbarWidth: "none" }}>
      <div className="px-5 pt-4 pb-24 space-y-5">
        <h2 className="text-xl font-extrabold text-foreground">Reports</h2>

        {/* Range selector */}
        <div className="bg-card rounded-2xl p-1.5 border border-border grid grid-cols-4 gap-1 shadow-sm">
          {["day", "week", "month", "semester"].map((r) => (
            <button
              key={r}
              onClick={() => setRange(r)}
              className={`py-2 rounded-xl text-xs font-bold capitalize transition-all ${
                range === r ? "bg-blue-600 text-white shadow-sm" : "text-muted-foreground"
              }`}
            >
              {r.charAt(0).toUpperCase() + r.slice(1)}
            </button>
          ))}
        </div>

        <div className="flex gap-2">
          <StatCard label="Present" value={183} color="text-emerald-600 dark:text-emerald-400" />
          <StatCard label="Late"    value={18}  color="text-amber-600 dark:text-amber-400" />
          <StatCard label="Absent"  value={21}  color="text-red-600 dark:text-red-400" />
        </div>

        {/* Chart */}
        <div className="bg-card rounded-2xl p-4 border border-border shadow-sm">
          <p className="text-sm font-bold text-foreground mb-4">Attendance by Day</p>
          <ResponsiveContainer width="100%" height={150}>
            <ReBarChart data={REPORT_DATA} barSize={10} barGap={3}>
              <XAxis dataKey="day" axisLine={false} tickLine={false} tick={{ fontSize: 11, fill: "#9ca3af" }} />
              <YAxis hide />
              <Tooltip
                contentStyle={{
                  background: "var(--card)",
                  border: "1px solid var(--border)",
                  borderRadius: "12px",
                  fontSize: 11,
                  color: "var(--foreground)",
                }}
                cursor={{ fill: "rgba(0,0,0,0.04)" }}
              />
              <Bar dataKey="present" fill="#10b981" radius={[4, 4, 0, 0]} />
              <Bar dataKey="late"    fill="#f59e0b" radius={[4, 4, 0, 0]} />
              <Bar dataKey="absent"  fill="#ef4444" radius={[4, 4, 0, 0]} />
            </ReBarChart>
          </ResponsiveContainer>
          <div className="flex items-center justify-center gap-5 mt-2">
            {[["#10b981", "Present"], ["#f59e0b", "Late"], ["#ef4444", "Absent"]].map(([color, label]) => (
              <span key={label} className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className="w-2 h-2 rounded-full" style={{ background: color }} />
                {label}
              </span>
            ))}
          </div>
        </div>

        {/* Export actions */}
        <div>
          <SecHdr title="Export & Send" />
          <div className="space-y-2.5">
            {[
              { Icon: Download, label: "Download PDF Report", sub: "Weekly summary · CS301", cl: "text-blue-600 dark:text-blue-400",     bg: "bg-blue-50 dark:bg-blue-950/30" },
              { Icon: Mail,     label: "Email to HOD",        sub: "hod.cs@college.edu.in",  cl: "text-purple-600 dark:text-purple-400", bg: "bg-purple-50 dark:bg-purple-950/30" },
              { Icon: Send,     label: "Notify Absentees",    sub: "Send warning to 6 students", cl: "text-teal-600 dark:text-teal-400", bg: "bg-teal-50 dark:bg-teal-950/30" },
            ].map(({ Icon, label, sub, cl, bg }) => (
              <motion.button key={label} className="w-full bg-card border border-border rounded-2xl p-3.5 flex items-center gap-3 shadow-sm text-left" whileTap={{ scale: 0.97 }}>
                <div className={`w-10 h-10 rounded-xl ${bg} flex items-center justify-center flex-shrink-0`}>
                  <Icon className={`w-5 h-5 ${cl}`} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-bold text-foreground">{label}</p>
                  <p className="text-xs text-muted-foreground">{sub}</p>
                </div>
                <ChevronRight className="w-4 h-4 text-muted-foreground" />
              </motion.button>
            ))}
          </div>
        </div>

        {/* Automation */}
        <div className="bg-card rounded-2xl border border-border p-4 space-y-3 shadow-sm">
          <p className="text-sm font-bold text-foreground">Automated Emails</p>
          {[
            { label: "3× Late Warning",   desc: "Sends HOD email on third late mark" },
            { label: "Weekly HOD Report", desc: "Every Monday at 08:00 AM" },
          ].map(({ label, desc }) => (
            <div key={label} className="flex items-center gap-3">
              <div className="w-2 h-2 rounded-full bg-emerald-500 flex-shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-foreground">{label}</p>
                <p className="text-xs text-muted-foreground">{desc}</p>
              </div>
              <span className="text-xs text-emerald-600 dark:text-emerald-400 font-bold">Active</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── SCREEN: QR Scanner ─────────────────────────────────────────────────────

function QRScannerScreen() {
  const [lastScan, setLastScan] = useState<string | null>(null);

  const handleScan = () => {
    setLastScan("CS21015 · Nikhil Sharma — Present · 09:06 AM");
    setTimeout(() => setLastScan(null), 3500);
  };

  return (
    <div className="absolute inset-0 bg-[#0A1020] flex flex-col">
      <div className="px-5 pt-4 pb-2 flex-shrink-0">
        <h2 className="text-xl font-extrabold text-white">QR Scanner</h2>
        <p className="text-xs text-slate-400 mt-0.5">Scan student QR code for manual attendance</p>
      </div>

      {/* Simulated camera */}
      <div className="flex-1 flex items-center justify-center relative bg-gradient-to-br from-slate-900 to-[#0A1020]">
        {/* Corner brackets */}
        <div className="relative w-60 h-60">
          {(["tl", "tr", "bl", "br"] as const).map((c) => (
            <div
              key={c}
              className={`absolute w-9 h-9 border-blue-400 ${
                c === "tl" ? "top-0 left-0 border-t-[3px] border-l-[3px] rounded-tl-xl" :
                c === "tr" ? "top-0 right-0 border-t-[3px] border-r-[3px] rounded-tr-xl" :
                c === "bl" ? "bottom-0 left-0 border-b-[3px] border-l-[3px] rounded-bl-xl" :
                             "bottom-0 right-0 border-b-[3px] border-r-[3px] rounded-br-xl"
              }`}
            />
          ))}
          {/* Scan line */}
          <motion.div
            className="absolute left-3 right-3 h-0.5 bg-gradient-to-r from-transparent via-blue-400 to-transparent"
            animate={{ top: ["6px", "calc(100% - 6px)", "6px"] }}
            transition={{ repeat: Infinity, duration: 2.8, ease: "easeInOut" }}
          />
          {/* Subtle QR dots */}
          <div className="absolute inset-8 grid grid-cols-6 grid-rows-6 gap-1 opacity-8">
            {Array.from({ length: 36 }).map((_, i) => (
              <div key={i} className={`rounded-sm ${Math.random() > 0.5 ? "bg-white/20" : ""}`} />
            ))}
          </div>
        </div>
        <p className="absolute bottom-8 text-xs text-slate-400">Position QR code within the frame</p>
      </div>

      {/* Controls */}
      <div className="bg-[#152035] px-5 pt-4 pb-24 space-y-3 flex-shrink-0">
        <AnimatePresence>
          {lastScan && (
            <motion.div
              className="bg-emerald-500/15 border border-emerald-500/35 rounded-xl p-3 flex items-center gap-2"
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
            >
              <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
              <p className="text-sm text-emerald-300 font-semibold">{lastScan}</p>
            </motion.div>
          )}
        </AnimatePresence>

        <div className="flex gap-3">
          <motion.button
            className="flex-1 bg-blue-600 text-white rounded-xl py-3 font-bold text-sm flex items-center justify-center gap-2"
            whileTap={{ scale: 0.96 }}
            onClick={handleScan}
          >
            <Scan className="w-4 h-4" />
            Simulate Scan
          </motion.button>
          <button className="w-12 h-12 bg-white/8 border border-white/10 rounded-xl flex items-center justify-center">
            <RefreshCw className="w-4 h-4 text-slate-400" />
          </button>
        </div>

        <div className="space-y-1.5">
          <p className="text-xs text-slate-500 font-bold uppercase tracking-widest">Recent Scans</p>
          {STAFF_STUDENTS.filter(s => s.status === "present").slice(0, 3).map((s) => (
            <div key={s.id} className="flex items-center gap-2.5 bg-white/5 rounded-xl px-3 py-2">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
              <span className="text-sm text-slate-300 flex-1">{s.name}</span>
              <span className="text-xs text-slate-500" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{s.time}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── SCREEN: Staff Settings ─────────────────────────────────────────────────

function StaffSettingsScreen({ isDark, onToggleDark, onLogout }: {
  isDark: boolean;
  onToggleDark: () => void;
  onLogout: () => void;
}) {
  const [emailNotif, setEmailNotif] = useState(true);
  const [pushNotif, setPushNotif] = useState(true);

  const prefs = [
    { label: "Dark Mode",           Icon: isDark ? Moon : Sun, val: isDark,    set: onToggleDark },
    { label: "Push Notifications",  Icon: Bell,                val: pushNotif, set: () => setPushNotif(v => !v) },
    { label: "Email Notifications", Icon: Mail,                val: emailNotif, set: () => setEmailNotif(v => !v) },
  ];

  return (
    <div className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] overflow-y-auto" style={{ scrollbarWidth: "none" }}>
      <div className="px-5 pt-4 pb-24 space-y-5">
        <h2 className="text-xl font-extrabold text-foreground">Settings</h2>

        <div className="bg-card rounded-2xl p-4 border border-border shadow-sm flex items-center gap-4">
          <Av initials="RK" size="lg" />
          <div>
            <h3 className="font-bold text-foreground text-base">{STAFF_USER.name}</h3>
            <p className="text-xs text-muted-foreground">{STAFF_USER.role}</p>
            <p className="text-xs text-muted-foreground">{STAFF_USER.dept}</p>
          </div>
        </div>

        <div>
          <SecHdr title="Preferences" />
          <div className="bg-card rounded-2xl border border-border shadow-sm overflow-hidden">
            {prefs.map(({ label, Icon, val, set }, i) => (
              <div key={label} className={`flex items-center gap-3 px-4 py-3.5 ${i < prefs.length - 1 ? "border-b border-border" : ""}`}>
                <div className="w-8 h-8 rounded-lg bg-muted flex items-center justify-center">
                  <Icon className="w-4 h-4 text-muted-foreground" />
                </div>
                <span className="flex-1 text-sm font-semibold text-foreground">{label}</span>
                <button
                  onClick={set}
                  className={`w-11 h-6 rounded-full relative transition-colors duration-200 ${val ? "bg-blue-600" : "bg-muted"}`}
                >
                  <motion.div
                    className="absolute top-0.5 bottom-0.5 w-5 rounded-full bg-white shadow"
                    animate={{ left: val ? "calc(100% - 1.375rem)" : "2px" }}
                    transition={{ type: "spring", stiffness: 500, damping: 32 }}
                  />
                </button>
              </div>
            ))}
          </div>
        </div>

        <div>
          <SecHdr title="Admin Tools" />
          <div className="bg-card rounded-2xl border border-border shadow-sm overflow-hidden">
            {[
              { label: "Audit History",    Icon: Eye,        sub: "View all attendance corrections"   },
              { label: "Export All Data",  Icon: Download,   sub: "Full semester backup — CSV / PDF"  },
              { label: "System Info",      Icon: Smartphone, sub: "ClassKey Enterprise · v2.4.1"      },
            ].map(({ label, Icon, sub }, i, arr) => (
              <div key={label} className={`flex items-center gap-3 px-4 py-3.5 ${i < arr.length - 1 ? "border-b border-border" : ""}`}>
                <div className="w-8 h-8 rounded-lg bg-muted flex items-center justify-center">
                  <Icon className="w-4 h-4 text-muted-foreground" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-foreground">{label}</p>
                  <p className="text-xs text-muted-foreground">{sub}</p>
                </div>
                <ChevronRight className="w-4 h-4 text-muted-foreground" />
              </div>
            ))}
          </div>
        </div>

        <motion.button
          className="w-full bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 rounded-2xl py-3.5 flex items-center justify-center gap-2 font-bold text-sm border border-red-100 dark:border-red-900/40"
          whileTap={{ scale: 0.97 }}
          onClick={onLogout}
        >
          <LogOut className="w-4 h-4" />
          Sign Out
        </motion.button>
      </div>
    </div>
  );
}

// ─── OVERLAY: Suspicious Review ─────────────────────────────────────────────

function SuspiciousOverlay({ onClose }: { onClose: () => void }) {
  return (
    <motion.div
      className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] flex flex-col z-10"
      initial={{ y: "100%" }}
      animate={{ y: 0 }}
      exit={{ y: "100%" }}
      transition={{ type: "spring", damping: 28, stiffness: 300 }}
    >
      <div className="px-5 pt-4 pb-3 flex items-center gap-3 border-b border-border">
        <BackBtn onBack={onClose} />
        <h2 className="flex-1 text-center font-bold text-foreground text-base mr-12">Suspicious Attempts</h2>
      </div>
      <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3 pb-10" style={{ scrollbarWidth: "none" }}>
        {SUSPICIOUS.map((s) => (
          <div key={s.id} className="bg-card rounded-2xl border border-border p-4 space-y-3 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="w-9 h-9 rounded-xl bg-amber-50 dark:bg-amber-950/30 flex items-center justify-center flex-shrink-0">
                <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-bold text-foreground">{s.student}</p>
                <p className="text-xs text-muted-foreground" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{s.rollNo}</p>
                <p className="text-xs text-amber-700 dark:text-amber-400 mt-1">{s.reason}</p>
                <p className="text-xs text-muted-foreground mt-0.5">Detected at {s.time}</p>
              </div>
            </div>
            <div className="flex gap-2">
              <button className="flex-1 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 rounded-xl py-2.5 text-sm font-bold border border-red-100 dark:border-red-900/40">
                Mark Absent
              </button>
              <button className="flex-1 bg-blue-600 text-white rounded-xl py-2.5 text-sm font-bold">
                Override Present
              </button>
            </div>
          </div>
        ))}
      </div>
    </motion.div>
  );
}

// ─── OVERLAY: Approvals ─────────────────────────────────────────────────────

function ApprovalsOverlay({ onClose }: { onClose: () => void }) {
  const [dismissed, setDismissed] = useState<number[]>([]);
  const visible = APPROVALS.filter((a) => !dismissed.includes(a.id));

  return (
    <motion.div
      className="absolute inset-0 bg-[#F0F4F8] dark:bg-[#0C1527] flex flex-col z-10"
      initial={{ y: "100%" }}
      animate={{ y: 0 }}
      exit={{ y: "100%" }}
      transition={{ type: "spring", damping: 28, stiffness: 300 }}
    >
      <div className="px-5 pt-4 pb-3 flex items-center gap-3 border-b border-border">
        <BackBtn onBack={onClose} />
        <h2 className="flex-1 text-center font-bold text-foreground text-base">Pending Approvals</h2>
        <span className="text-xs bg-orange-100 dark:bg-orange-950/40 text-orange-700 dark:text-orange-400 px-2 py-0.5 rounded-full font-bold min-w-[1.5rem] text-center">{visible.length}</span>
      </div>
      <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3 pb-10" style={{ scrollbarWidth: "none" }}>
        <AnimatePresence>
          {visible.map((a) => (
            <motion.div
              key={a.id}
              className="bg-card rounded-2xl border border-border p-4 shadow-sm space-y-3"
              exit={{ opacity: 0, scale: 0.95 }}
              transition={{ duration: 0.2 }}
            >
              <div className="flex items-start gap-3">
                <Av initials={a.student.split(" ").map(n => n[0]).join("").slice(0, 2)} size="sm" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-bold text-foreground">{a.student}</p>
                  <p className="text-xs text-muted-foreground" style={{ fontFamily: "'JetBrains Mono', monospace" }}>{a.rollNo}</p>
                  <div className="flex items-center gap-2 mt-1.5">
                    <span className="text-xs font-bold text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-950/30 px-2 py-0.5 rounded-lg">{a.type}</span>
                    <span className="text-xs text-muted-foreground">{a.date}</span>
                  </div>
                  <p className="text-xs text-foreground mt-1.5">{a.reason}</p>
                </div>
              </div>
              <div className="flex gap-2">
                <motion.button
                  onClick={() => setDismissed(d => [...d, a.id])}
                  className="flex-1 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 rounded-xl py-2.5 text-sm font-bold border border-red-100 dark:border-red-900/40"
                  whileTap={{ scale: 0.96 }}
                >
                  Reject
                </motion.button>
                <motion.button
                  onClick={() => setDismissed(d => [...d, a.id])}
                  className="flex-1 bg-emerald-600 text-white rounded-xl py-2.5 text-sm font-bold"
                  whileTap={{ scale: 0.96 }}
                >
                  Approve
                </motion.button>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>

        {visible.length === 0 && (
          <motion.div
            className="py-16 text-center"
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
          >
            <CheckCheck className="w-12 h-12 text-emerald-500 mx-auto mb-3" />
            <p className="text-sm font-bold text-foreground">All caught up!</p>
            <p className="text-xs text-muted-foreground mt-1">No pending approvals</p>
          </motion.div>
        )}
      </div>
    </motion.div>
  );
}

// ─── Tab bars ───────────────────────────────────────────────────────────────

const STUDENT_TABS: { id: StudentScreen; label: string; Icon: LucideIcon }[] = [
  { id: "home",          label: "Home",    Icon: Home     },
  { id: "history",       label: "History", Icon: History  },
  { id: "permissions",   label: "Permits", Icon: FileText },
  { id: "notifications", label: "Alerts",  Icon: Bell     },
  { id: "profile",       label: "Profile", Icon: User     },
];

const STAFF_TABS: { id: StaffScreen; label: string; Icon: LucideIcon }[] = [
  { id: "dashboard", label: "Home",     Icon: Home      },
  { id: "students",  label: "Students", Icon: Users     },
  { id: "reports",   label: "Reports",  Icon: BarChart3 },
  { id: "qr",        label: "QR",       Icon: QrCode    },
  { id: "settings",  label: "Settings", Icon: Settings  },
];

function TabBar<T extends string>({
  tabs, active, onChange, badges,
}: {
  tabs: { id: T; label: string; Icon: LucideIcon }[];
  active: T;
  onChange: (id: T) => void;
  badges?: T[];
}) {
  return (
    <div className="absolute bottom-0 left-0 right-0 bg-card border-t border-border pb-2 pt-1 px-1 flex justify-around z-30" style={{ backdropFilter: "blur(12px)" }}>
      {tabs.map(({ id, label, Icon }) => {
        const isActive = active === id;
        const hasBadge = badges?.includes(id);
        return (
          <button
            key={id}
            onClick={() => onChange(id)}
            className="flex flex-col items-center gap-0.5 py-1.5 px-3 rounded-xl min-w-[3rem]"
          >
            <div className="relative">
              <Icon className={`w-5 h-5 transition-colors duration-150 ${isActive ? "text-blue-600 dark:text-blue-400" : "text-muted-foreground"}`} />
              {hasBadge && <span className="absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full bg-red-500 border-2 border-card" />}
            </div>
            <span className={`text-[10px] font-bold transition-colors duration-150 ${isActive ? "text-blue-600 dark:text-blue-400" : "text-muted-foreground"}`}>
              {label}
            </span>
          </button>
        );
      })}
    </div>
  );
}

// ─── Status bar ─────────────────────────────────────────────────────────────

function StatusBar() {
  return (
    <div className="flex-shrink-0 h-11 px-6 flex items-center justify-between relative z-20">
      <span className="text-xs font-bold text-foreground">9:41</span>
      <div className="flex items-center gap-1.5 text-foreground">
        <div className="flex gap-0.5 items-end">
          {[1, 1.5, 2, 2.5].map((h, i) => (
            <div key={i} className="w-0.5 rounded-full bg-current" style={{ height: `${h * 4}px`, opacity: i < 3 ? 0.5 + i * 0.2 : 1 }} />
          ))}
        </div>
        <svg width="13" height="9" viewBox="0 0 13 9" fill="none">
          <rect x="0.5" y="0.5" width="11" height="8" rx="1.5" stroke="currentColor" strokeOpacity="0.5" />
          <rect x="12" y="2.5" width="1" height="4" rx="0.5" fill="currentColor" fillOpacity="0.4" />
          <rect x="1.5" y="1.5" width="8" height="6" rx="1" fill="currentColor" />
        </svg>
      </div>
    </div>
  );
}

// ─── App ────────────────────────────────────────────────────────────────────

export default function App() {
  const [phase, setPhase] = useState<"splash" | "login" | "app">("splash");
  const [role, setRole] = useState<Role | null>(null);
  const [sTab, setSTab] = useState<StudentScreen>("home");
  const [stTab, setStTab] = useState<StaffScreen>("dashboard");
  const [overlay, setOverlay] = useState<Overlay>(null);
  const [isDark, setIsDark] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setPhase("login"), 2400);
    return () => clearTimeout(t);
  }, []);

  const handleLogin = (r: Role) => { setRole(r); setPhase("app"); };
  const handleLogout = () => { setRole(null); setPhase("login"); setOverlay(null); setSTab("home"); setStTab("dashboard"); };

  const hasOverlay = overlay !== null;

  return (
    <div className={isDark ? "dark" : ""}>
      <div className="min-h-screen bg-slate-300 dark:bg-slate-950 flex flex-col items-center justify-center p-4 transition-colors duration-300 gap-4">
        {/* Phone frame */}
        <div className="relative w-[390px] h-[844px] rounded-[2.75rem] overflow-hidden shadow-2xl shadow-black/30 border border-black/10 flex flex-col bg-[#F0F4F8] dark:bg-[#0C1527] transition-colors duration-300">
          <StatusBar />

          {/* Screen content area */}
          <div className="flex-1 relative overflow-hidden">
            <AnimatePresence mode="wait">
              {phase === "splash" && <SplashScreen key="splash" />}
              {phase === "login"  && <LoginScreen key="login" onLogin={handleLogin} />}

              {phase === "app" && role === "student" && (
                <motion.div key="student" className="absolute inset-0" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                  {/* Tabs */}
                  <AnimatePresence mode="wait">
                    {sTab === "home"          && !hasOverlay && <StudentHome key="home" onMarkAttendance={() => setOverlay("mark-attendance")} onTab={setSTab} />}
                    {sTab === "history"        && !hasOverlay && <HistoryScreen key="hist" />}
                    {sTab === "permissions"    && !hasOverlay && <PermissionsScreen key="perm" onNewRequest={() => setOverlay("new-permission")} />}
                    {sTab === "notifications"  && !hasOverlay && <NotificationsScreen key="notif" />}
                    {sTab === "profile"        && !hasOverlay && <ProfileScreen key="profile" isDark={isDark} onToggleDark={() => setIsDark(d => !d)} onLogout={handleLogout} />}
                  </AnimatePresence>
                  {/* Overlays */}
                  <AnimatePresence>
                    {overlay === "mark-attendance"  && <MarkAttendanceScreen key="mark" onClose={() => setOverlay(null)} onSuccess={() => setOverlay("attendance-success")} />}
                    {overlay === "attendance-success" && <AttendanceSuccessScreen key="succ" onClose={() => { setOverlay(null); setSTab("home"); }} />}
                    {overlay === "new-permission"   && <NewPermissionForm key="nperm" onClose={() => setOverlay(null)} />}
                  </AnimatePresence>
                  {/* Tab bar */}
                  {!hasOverlay && (
                    <TabBar
                      tabs={STUDENT_TABS} active={sTab} onChange={setSTab}
                      badges={["notifications"]}
                    />
                  )}
                </motion.div>
              )}

              {phase === "app" && role === "staff" && (
                <motion.div key="staff" className="absolute inset-0" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                  <AnimatePresence mode="wait">
                    {stTab === "dashboard" && !hasOverlay && <StaffDashboard key="dash" onSuspicious={() => setOverlay("suspicious")} onApprovals={() => setOverlay("approvals")} onTab={setStTab} />}
                    {stTab === "students"  && !hasOverlay && <StudentListScreen key="slist" />}
                    {stTab === "reports"   && !hasOverlay && <ReportsScreen key="rpts" />}
                    {stTab === "qr"        && !hasOverlay && <QRScannerScreen key="qr" />}
                    {stTab === "settings"  && !hasOverlay && <StaffSettingsScreen key="stset" isDark={isDark} onToggleDark={() => setIsDark(d => !d)} onLogout={handleLogout} />}
                  </AnimatePresence>
                  <AnimatePresence>
                    {overlay === "suspicious" && <SuspiciousOverlay key="sus" onClose={() => setOverlay(null)} />}
                    {overlay === "approvals"  && <ApprovalsOverlay  key="app" onClose={() => setOverlay(null)} />}
                  </AnimatePresence>
                  {!hasOverlay && (
                    <TabBar
                      tabs={STAFF_TABS} active={stTab} onChange={setStTab}
                      badges={stTab === "dashboard" ? [] : ["dashboard"]}
                    />
                  )}
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        {/* View switcher */}
        {phase === "app" && (
          <div className="flex items-center gap-2 bg-white/80 dark:bg-slate-800/80 backdrop-blur-sm rounded-full px-2 py-1.5 shadow-sm">
            <button
              onClick={() => { setRole("student"); setSTab("home"); setOverlay(null); }}
              className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${role === "student" ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 dark:text-slate-400"}`}
            >
              Student View
            </button>
            <button
              onClick={() => { setRole("staff"); setStTab("dashboard"); setOverlay(null); }}
              className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${role === "staff" ? "bg-blue-600 text-white shadow-sm" : "text-slate-500 dark:text-slate-400"}`}
            >
              Staff View
            </button>
            <div className="w-px h-4 bg-slate-300 dark:bg-slate-600" />
            <button
              onClick={() => setIsDark(d => !d)}
              className="w-7 h-7 rounded-full flex items-center justify-center text-slate-500 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
            >
              {isDark ? <Sun className="w-3.5 h-3.5" /> : <Moon className="w-3.5 h-3.5" />}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
