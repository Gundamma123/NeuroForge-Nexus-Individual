import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import Sidebar from "../components/Sidebar";
import Footer from "../components/Footer";
import { getCurrentUser } from "../services/userService";
import { getAllProjects } from "../services/projectService";
import { getAllTeams } from "../services/teamService";
import { getAllSprints } from "../services/sprintService";
import { getAllTasks } from "../services/taskService";

const ProjectDashboard = () => {
  const navigate = useNavigate();
  const user = getCurrentUser();
  const firstName = (user?.name || "there").split(" ")[0];

  const [projects, setProjects] = useState([]);
  const [teams, setTeams] = useState([]);
  const [sprints, setSprints] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getAllProjects(), getAllTeams(), getAllSprints(), getAllTasks()])
      .then(([p, t, s, tk]) => {
        if (cancelled) return;
        if (p.status === "fulfilled" && Array.isArray(p.value)) setProjects(p.value);
        if (t.status === "fulfilled" && Array.isArray(t.value)) setTeams(t.value);
        if (s.status === "fulfilled" && Array.isArray(s.value)) setSprints(s.value);
        if (tk.status === "fulfilled" && Array.isArray(tk.value)) setTasks(tk.value);
        setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  const stats = useMemo(() => ({
    totalProjects: projects.length,
    activeProjects: projects.filter((p) => p.status === "Active").length,
    totalTeams: teams.length,
    activeSprints: sprints.filter((s) => s.status === "Active").length,
    totalTasks: tasks.length,
    doneTasks: tasks.filter((t) => t.status === "Done").length,
    overdueTasks: tasks.filter((t) => {
      if (!t.dueDate || t.status === "Done") return false;
      return new Date(t.dueDate) < new Date();
    }).length,
  }), [projects, teams, sprints, tasks]);

  const recentProjects = useMemo(() => projects.slice(0, 5), [projects]);
  const activeSprints = useMemo(() => sprints.filter((s) => s.status === "Active").slice(0, 5), [sprints]);

  return (
    <div className="nf-app">
      <Navbar />
      <div className="nf-body">
        <Sidebar />
        <main className="nf-main">
          {/* <div className="nf-toolbar" style={{ marginBottom: 4 }}>
            <div>
              <h1 className="nf-page-title" style={{ marginBottom: 2 }}>Welcome back, {firstName}</h1>
              <div className="nf-detail-row" style={{ margin: 0 }}>Project Management Dashboard</div>
            </div>
          </div> */}
          
<div className="nf-banner">
  <div>
    <h1>Welcome back, {firstName}</h1>
    <p>Project Management Dashboard</p>
  </div>
</div>


          {loading && <div className="nf-loading" style={{ marginTop: 16 }}>Loading dashboard...</div>}

          {!loading && (
            <>
              <div className="nf-stat-grid" style={{ marginTop: 16 }}>
                <div className="nf-card" style={{ cursor: "pointer" }} onClick={() => navigate("/projects")}>
                  <div className="nf-stat-label">Total Projects</div>
                  <div className="nf-stat-value">{stats.totalProjects}</div>
                  <div className="nf-stat-tag">{stats.activeProjects} active</div>
                </div>
                <div className="nf-card" style={{ cursor: "pointer" }} onClick={() => navigate("/teams")}>
                  <div className="nf-stat-label">Teams</div>
                  <div className="nf-stat-value">{stats.totalTeams}</div>
                  <div className="nf-stat-tag">assigned</div>
                </div>
                <div className="nf-card" style={{ cursor: "pointer" }} onClick={() => navigate("/sprints")}>
                  <div className="nf-stat-label">Active Sprints</div>
                  <div className="nf-stat-value">{stats.activeSprints}</div>
                  <div className="nf-stat-tag">in progress</div>
                </div>
                <div className="nf-card">
                  <div className="nf-stat-label">Tasks Done</div>
                  <div className="nf-stat-value">{stats.doneTasks} / {stats.totalTasks}</div>
                  <div className="nf-stat-tag">
                    {stats.totalTasks > 0
                      ? `${Math.round((stats.doneTasks / stats.totalTasks) * 100)}% complete`
                      : "no tasks yet"}
                  </div>
                </div>
                <div className="nf-card">
                  <div className="nf-stat-label">Overdue Tasks</div>
                  <div className="nf-stat-value" style={{ color: stats.overdueTasks > 0 ? "var(--nf-danger, #ef4444)" : undefined }}>
                    {stats.overdueTasks}
                  </div>
                  <div className="nf-stat-tag">past due date</div>
                </div>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20, marginTop: 20 }}>
                <div className="nf-panel">
                  <div className="nf-toolbar">
                    <h3 style={{ margin: 0 }}>Recent Projects</h3>
                  </div>
                  {recentProjects.length === 0
                    ? <div className="nf-empty-state">No projects yet.</div>
                    : recentProjects.map((p) => (
                      <div key={p.id} className="nf-detail-row"
                        style={{ display: "flex", justifyContent: "space-between", alignItems: "center", cursor: "pointer" }}
                        onClick={() => navigate(`/projects/${p.id}`)}>
                        <span><b>{p.name}</b></span>
                        <span className={`nf-badge ${p.status === "Active" ? "status-inprogress" : p.status === "Completed" ? "status-done" : ""}`}>
                          {p.status}
                        </span>
                      </div>
                    ))
                  }
                </div>

                <div className="nf-panel">
                  <div className="nf-toolbar">
                    <h3 style={{ margin: 0 }}>Active Sprints</h3>
                  </div>
                  {activeSprints.length === 0
                    ? <div className="nf-empty-state">No active sprints.</div>
                    : activeSprints.map((s) => (
                      <div key={s.id} className="nf-detail-row"
                        style={{ display: "flex", justifyContent: "space-between", alignItems: "center", cursor: "pointer" }}
                        onClick={() => navigate(`/sprints/${s.id}`)}>
                        <span><b>{s.name}</b></span>
                        <span style={{ fontSize: 12, color: "var(--nf-text-faint)" }}>
                          {s.endDate ? `ends ${s.endDate}` : "no end date"}
                        </span>
                      </div>
                    ))
                  }
                </div>
              </div>

              <div className="nf-actions" style={{ marginTop: 20 }}>
                <button className="nf-btn" onClick={() => navigate("/projects/create")}>+ Create Project</button>
                <button className="nf-btn secondary" onClick={() => navigate("/projects")}>All Projects</button>
                <button className="nf-btn secondary" onClick={() => navigate("/sprints")}>All Sprints</button>
              </div>
            </>
          )}
        </main>
      </div>
      <Footer />
    </div>
  );
};

export default ProjectDashboard;
