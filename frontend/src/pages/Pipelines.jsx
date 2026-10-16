// import { useEffect, useMemo, useState } from "react";
// import { useNavigate } from "react-router-dom";
// import { RotateCw, ExternalLink } from "lucide-react";
// import Navbar from "../components/Navbar";
// import Sidebar from "../components/Sidebar";
// import Footer from "../components/Footer";
// import { getAllProjects } from "../services/projectService";
// import { getRepoLink, getRepoActivity, rerunWorkflowRun } from "../services/githubService";

// const STATUS_CLASS = {
//   success: "status-done",
//   failure: "status-todo",
//   cancelled: "status-todo",
//   timed_out: "status-todo",
//   in_progress: "status-inprogress",
//   queued: "status-inprogress",
//   skipped: "",
// };

// const timeAgo = (iso) => {
//   if (!iso) return "—";
//   const diff = Date.now() - new Date(iso).getTime();
//   if (Number.isNaN(diff)) return "—";
//   const mins = Math.floor(diff / 60000);
//   if (mins < 1) return "just now";
//   if (mins < 60) return `${mins}m ago`;
//   const hrs = Math.floor(mins / 60);
//   if (hrs < 24) return `${hrs}h ago`;
//   return `${Math.floor(hrs / 24)}d ago`;
// };

// const duration = (start, end) => {
//   if (!start || !end) return "—";
//   const secs = Math.round((new Date(end) - new Date(start)) / 1000);
//   if (secs < 60) return `${secs}s`;
//   const m = Math.floor(secs / 60), s = secs % 60;
//   return `${m}m ${s}s`;
// };

// const statusOf = (r) => (r.status === "completed" ? r.conclusion : r.status);

// const openUrl = (url) => {
//   if (typeof url === "string" && url.startsWith("https://"))
//     window.open(url, "_blank", "noopener,noreferrer");
// };

// const Pipelines = () => {
//   const navigate = useNavigate();
//   const [rows, setRows] = useState([]);
//   const [loading, setLoading] = useState(true);
//   const [refreshing, setRefreshing] = useState(false);
//   const [reloadKey, setReloadKey] = useState(0);
//   const [busyRun, setBusyRun] = useState(null);
//   const [notice, setNotice] = useState("");
//   const [error, setError] = useState("");
//   const [statusFilter, setStatusFilter] = useState("");

//   const load = async (isRefresh = false) => {
//     if (isRefresh) setRefreshing(true);
//     else setLoading(true);
//     setError("");
//     try {
//       const projects = await getAllProjects();
//       const list = Array.isArray(projects) ? projects : [];
//       const results = await Promise.all(
//         list.map(async (project) => {
//           try {
//             const link = await getRepoLink(project.id);
//             if (!link) return { project, link: null, runs: [], rowError: null };
//             const runs = await getRepoActivity(project.id, "runs");
//             return { project, link, runs: Array.isArray(runs) ? runs : [], rowError: null };
//           } catch (err) {
//             return { project, link: null, runs: [], rowError: err?.response?.data?.message || "Could not load." };
//           }
//         })
//       );
//       setRows(results);
//     } catch (err) {
//       setError(err?.response?.data?.message || "Could not load projects.");
//     } finally {
//       setLoading(false);
//       setRefreshing(false);
//     }
//   };

//   useEffect(() => { load(); }, [reloadKey]); // eslint-disable-line react-hooks/exhaustive-deps

//   const summary = useMemo(() => {
//     let total = 0, success = 0, failed = 0, running = 0;
//     rows.forEach(({ runs }) => runs.forEach((r) => {
//       total++;
//       const k = statusOf(r);
//       if (k === "success") success++;
//       else if (["failure", "cancelled", "timed_out"].includes(k)) failed++;
//       else if (["in_progress", "queued"].includes(k)) running++;
//     }));
//     return { total, success, failed, running };
//   }, [rows]);

//   const rerun = async (project, run) => {
//     if (!window.confirm(`Re-run "${run.name}" on branch ${run.branch}?`)) return;
//     setBusyRun(run.id);
//     setError(""); setNotice("");
//     try {
//       const res = await rerunWorkflowRun(project.id, run.id);
//       setNotice(res?.message || "Workflow re-run requested.");
//       await new Promise((r) => setTimeout(r, 1500));
//       setReloadKey((k) => k + 1);
//     } catch (err) {
//       setError(err?.response?.data?.message || "Could not re-run the workflow.");
//     } finally {
//       setBusyRun(null);
//     }
//   };

//   return (
//     <div className="nf-app">
//       <Navbar />
//       <div className="nf-body">
//         <Sidebar />
//         <main className="nf-main">
//           <span className="nf-back-link" onClick={() => navigate("/delivery")}>← Delivery Dashboard</span>

//           <div className="nf-toolbar" style={{ marginBottom: 4 }}>
//             <div>
//               <h1 className="nf-page-title" style={{ marginBottom: 2 }}>Pipelines</h1>
//               <div className="nf-detail-row" style={{ margin: 0 }}>
//                 Live GitHub Actions workflow runs for each project's linked repository.
//               </div>
//             </div>
//             <div style={{ flex: 1 }} />
//             <select
//               className="nf-select-filter"
//               value={statusFilter}
//               onChange={(e) => setStatusFilter(e.target.value)}
//             >
//               <option value="">All statuses</option>
//               <option value="success">Success</option>
//               <option value="failure">Failure</option>
//               <option value="cancelled">Cancelled</option>
//               <option value="in_progress">In Progress</option>
//               <option value="queued">Queued</option>
//             </select>
//             <button className="nf-btn" onClick={() => load(true)} disabled={refreshing || loading}>
//               <RotateCw size={13} /> {refreshing ? "Refreshing..." : "Refresh"}
//             </button>
//           </div>

//           {notice && <div className="nf-gh-notice" style={{ marginBottom: 12 }}>{notice}</div>}
//           {error && <div className="nf-form-error">{error}</div>}

//           {/* Summary stats */}
//           {!loading && (
//             <div className="nf-stat-grid" style={{ marginBottom: 20 }}>
//               <div className="nf-card">
//                 <div className="nf-stat-label">Total Runs</div>
//                 <div className="nf-stat-value">{summary.total}</div>
//                 <div className="nf-stat-tag">across all projects</div>
//               </div>
//               <div className="nf-card">
//                 <div className="nf-stat-label">Passed</div>
//                 <div className="nf-stat-value" style={{ color: "var(--nf-success, #22c55e)" }}>{summary.success}</div>
//                 <div className="nf-stat-tag">
//                   {summary.total > 0 ? `${Math.round((summary.success / summary.total) * 100)}% success rate` : "—"}
//                 </div>
//               </div>
//               <div className="nf-card">
//                 <div className="nf-stat-label">Failed</div>
//                 <div className="nf-stat-value" style={{ color: summary.failed > 0 ? "#ef4444" : undefined }}>
//                   {summary.failed}
//                 </div>
//                 <div className="nf-stat-tag">need attention</div>
//               </div>
//               <div className="nf-card">
//                 <div className="nf-stat-label">Running Now</div>
//                 <div className="nf-stat-value" style={{ color: summary.running > 0 ? "var(--nf-cyan-soft)" : undefined }}>
//                   {summary.running}
//                 </div>
//                 <div className="nf-stat-tag">in_progress / queued</div>
//               </div>
//             </div>
//           )}

//           {loading && <div className="nf-loading">Loading pipelines...</div>}

//           {!loading && rows.map(({ project, link, runs, rowError }) => {
//             const filtered = statusFilter
//               ? runs.filter((r) => statusOf(r) === statusFilter)
//               : runs;

//             return (
//               <div className="nf-panel" style={{ marginBottom: 16 }} key={project.id}>
//                 <div className="nf-toolbar" style={{ marginBottom: 10 }}>
//                   <div>
//                     <b style={{ fontSize: 15 }}>{project.name}</b>
//                     {link && (
//                       <span style={{ color: "var(--nf-text-faint)", fontSize: 12, marginLeft: 8 }}>
//                         {link.repoOwner}/{link.repoName}
//                       </span>
//                     )}
//                   </div>
//                   {link && !rowError && (
//                     <span style={{ color: "var(--nf-text-faint)", fontSize: 12 }}>
//                       {runs.length} runs
//                     </span>
//                   )}
//                 </div>

//                 {!link ? (
//                   <div className="nf-empty-state">
//                     No GitHub repository linked.{" "}
//                     <span
//                       className="nf-link-btn"
//                       style={{ display: "inline" }}
//                       onClick={() => navigate(`/projects/${project.id}`)}
//                     >
//                       Link one from the project page.
//                     </span>
//                   </div>
//                 ) : rowError ? (
//                   <div className="nf-form-error">{rowError}</div>
//                 ) : filtered.length === 0 ? (
//                   <div className="nf-empty-state">
//                     {runs.length === 0
//                       ? `No workflow runs found for ${link.repoOwner}/${link.repoName}.`
//                       : "No runs match the selected filter."}
//                   </div>
//                 ) : (
//                   <div className="nf-table-wrap">
//                     <table className="nf-table">
//                       <thead>
//                         <tr>
//                           <th>Workflow</th>
//                           <th>Branch</th>
//                           <th>Status</th>
//                           <th>Triggered by</th>
//                           <th>Duration</th>
//                           <th>When</th>
//                           <th>Actions</th>
//                         </tr>
//                       </thead>
//                       <tbody>
//                         {filtered.map((r) => {
//                           const key = statusOf(r);
//                           return (
//                             <tr key={r.id} className="clickable" onClick={() => openUrl(r.url)}>
//                               <td><b>{r.name}</b></td>
//                               <td>{r.branch}</td>
//                               <td>
//                                 <span className={`nf-badge ${STATUS_CLASS[key] || ""}`}>
//                                   {key || "unknown"}
//                                 </span>
//                               </td>
//                               <td>{r.actor || "—"}</td>
//                               <td style={{ color: "var(--nf-text-faint)", fontSize: 12 }}>
//                                 {duration(r.createdAt, r.updatedAt)}
//                               </td>
//                               <td style={{ color: "var(--nf-text-faint)", fontSize: 12 }}>
//                                 {timeAgo(r.createdAt)}
//                               </td>
//                               <td onClick={(e) => e.stopPropagation()}>
//                                 <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
//                                   {r.status === "completed" && (
//                                     <button
//                                       className="nf-gh-action-btn"
//                                       disabled={busyRun === r.id}
//                                       onClick={() => rerun(project, r)}
//                                     >
//                                       <RotateCw size={12} />
//                                       {busyRun === r.id ? "Re-running..." : "Re-run"}
//                                     </button>
//                                   )}
//                                   {r.url && r.url.startsWith("https://") && (
//                                     <a
//                                       href={r.url}
//                                       target="_blank"
//                                       rel="noreferrer"
//                                       style={{ color: "var(--nf-cyan-soft)", display: "flex" }}
//                                       title="Open on GitHub"
//                                     >
//                                       <ExternalLink size={13} />
//                                     </a>
//                                   )}
//                                 </div>
//                               </td>
//                             </tr>
//                           );
//                         })}
//                       </tbody>
//                     </table>
//                   </div>
//                 )}
//               </div>
//             );
//           })}

//           {!loading && rows.length === 0 && !error && (
//             <div className="nf-empty-state">No projects found.</div>
//           )}
//         </main>
//       </div>
//       <Footer />
//     </div>
//   );
// };

// export default Pipelines;

import { useEffect, useState } from "react";
import { RotateCw } from "lucide-react";
import Navbar from "../components/Navbar";
import Sidebar from "../components/Sidebar";
import Footer from "../components/Footer";
import { getAllProjects } from "../services/projectService";
import { getRepoLink, getRepoActivity, rerunWorkflowRun } from "../services/githubService";

const STATUS_CLASS = {
  success: "status-done",
  failure: "status-todo",
  in_progress: "status-inprogress",
  queued: "status-inprogress",
};

const timeAgo = (iso) => {
  if (!iso) return "—";
  const diff = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(diff)) return "—";
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
};

const openUrl = (url) => {
  if (typeof url === "string" && url.startsWith("https://")) {
    window.open(url, "_blank", "noopener,noreferrer");
  }
};

// Loads one project's link and runs. The link is kept even when the runs
// request fails, so the page can show GitHub's reason instead of
// "no repository linked".
const loadProject = async (project) => {
  let link = null;
  try {
    link = await getRepoLink(project.id);
    if (!link) return { project, link: null, runs: [], error: null };
    const runs = await getRepoActivity(project.id, "runs");
    return { project, link, runs: Array.isArray(runs) ? runs : [], error: null };
  } catch (err) {
    return {
      project,
      link,
      runs: [],
      error: err?.response?.data?.message || "Could not load workflow runs.",
    };
  }
};

const Pipelines = () => {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [busyRun, setBusyRun] = useState(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    getAllProjects()
      .then(async (projects) => {
        const list = Array.isArray(projects) ? projects : [];
        const results = await Promise.all(list.map(loadProject));
        if (!cancelled) setRows(results);
      })
      .catch(() => { if (!cancelled) setError("Could not load projects."); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [reloadKey]);

  const rerun = async (project, run) => {
    if (!window.confirm(`Re-run "${run.name}" on branch ${run.branch}?`)) return;
    setBusyRun(run.id);
    setError("");
    setNotice("");
    try {
      const res = await rerunWorkflowRun(project.id, run.id);
      setNotice(res?.message || "Workflow re-run requested.");
      await new Promise((resolve) => setTimeout(resolve, 1500)); // let GitHub register the new run
      setReloadKey((k) => k + 1);
    } catch (err) {
      setError(err?.response?.data?.message || "Could not re-run the workflow.");
    } finally {
      setBusyRun(null);
    }
  };

  return (
    <div className="nf-app">
      <Navbar />
      <div className="nf-body">
        <Sidebar />
        <main className="nf-main">
          <h1 className="nf-page-title">Pipelines</h1>
          <p className="nf-detail-row" style={{ marginTop: -8 }}>
            Live GitHub Actions workflow runs for each project's linked repository.
          </p>

          {notice && <div className="nf-gh-notice">{notice}</div>}
          {error && <div className="nf-form-error">{error}</div>}
          {loading && <div className="nf-loading">Loading pipelines...</div>}

          {!loading && rows.map(({ project, link, runs, error: rowError }) => (
            <div className="nf-panel" style={{ marginBottom: 16 }} key={project.id}>
              <h3>{project.name}</h3>

              {rowError ? (
                <div className="nf-form-error">{rowError}</div>
              ) : !link ? (
                <div className="nf-empty-state">No GitHub repository linked to this project.</div>
              ) : runs.length === 0 ? (
                <div className="nf-empty-state">
                  No workflow runs found for {link.repoOwner}/{link.repoName}.
                </div>
              ) : (
                <div className="nf-table-wrap">
                  <table className="nf-table">
                    <thead>
                      <tr>
                        <th>Workflow</th><th>Branch</th><th>Status</th><th>Triggered by</th><th>When</th><th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {runs.map((r) => {
                        const statusKey = r.status === "completed" ? r.conclusion : r.status;
                        return (
                          <tr key={r.id} className="clickable" onClick={() => openUrl(r.url)}>
                            <td><b>{r.name}</b></td>
                            <td>{r.branch}</td>
                            <td>
                              <span className={`nf-badge ${STATUS_CLASS[statusKey] || ""}`}>{statusKey || "unknown"}</span>
                            </td>
                            <td>{r.actor}</td>
                            <td>{timeAgo(r.createdAt)}</td>
                            <td onClick={(e) => e.stopPropagation()}>
                              {r.status === "completed" && (
                                <button
                                  className="nf-gh-action-btn"
                                  disabled={busyRun === r.id}
                                  onClick={() => rerun(project, r)}
                                >
                                  <RotateCw size={12} /> {busyRun === r.id ? "Re-running..." : "Re-run"}
                                </button>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ))}
        </main>
      </div>
      <Footer />
    </div>
  );
};

export default Pipelines;
