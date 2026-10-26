import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { tabContentVariants } from '../../../../shared/motion/motionTokens';
import { useAuth } from '../../../../context/AuthContext';
import {
  BorrowingItem,
  CreateImplementPayload,
  FACULTY_LABELS,
  FacultyCode,
  IMPLEMENT_CATEGORY_LABELS,
  IMPLEMENT_STATE_LABELS,
  ImplementItem,
  ReserveItem,
  UpdateImplementPayload,
} from '../../../../types/equipment';
import {
  cancelReserve,
  confirmReserve,
  createImplement,
  deleteImplement,
  getBorrowings,
  getImplements,
  getReserves,
  returnBorrowing,
  updateImplement,
} from '../../../../api/equipment';
import { EquipmentFormModal } from './EquipmentFormModal';
import { Pagination } from '../../../../shared/components/ui/Pagination';
import { ConfirmDialog } from '../../../../shared/components/ui/ConfirmDialog';
import { LoadingSpinner } from '../../../../shared/components/ui/LoadingSpinner';
import { ErrorAlert } from '../../../../shared/components/ui/ErrorAlert';

export interface InventoryAdminPanelProps {
  isSystemAdmin?: boolean;
}

export function InventoryAdminPanel({
  isSystemAdmin = false,
}: InventoryAdminPanelProps): React.JSX.Element {
  const { token, profile } = useAuth();
  const [activeTab, setActiveTab] = useState<'implements' | 'reserves' | 'borrowings'>('implements');

  // Datos
  const [implementsList, setImplementsList] = useState<ImplementItem[]>([]);
  const [reservesList, setReservesList] = useState<ReserveItem[]>([]);
  const [borrowingsList, setBorrowingsList] = useState<BorrowingItem[]>([]);

  // Estados de carga
  const [loading, setLoading] = useState(true);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Filtros implementos
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [facultyFilter, setFacultyFilter] = useState('');
  const [stateFilter, setStateFilter] = useState('');

  // Modales
  const [formModalOpen, setFormModalOpen] = useState(false);
  const [implementToEdit, setImplementToEdit] = useState<ImplementItem | null>(null);
  const [savingImplement, setSavingImplement] = useState(false);

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [itemToDelete, setItemToDelete] = useState<ImplementItem | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Paginación
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  const userFaculty = (profile?.faculty as FacultyCode) || null;

  // Carga general de datos
  const loadData = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const [allImplements, allReserves, allBorrowings] = await Promise.all([
        getImplements(undefined, token),
        getReserves(token),
        getBorrowings(token),
      ]);

      // Si es Implementos admin, filtrar por su facultad
      if (!isSystemAdmin && userFaculty) {
        setImplementsList(allImplements.filter((i) => i.faculty === userFaculty));
        setReservesList(
          allReserves.filter((r) => {
            const fac = typeof r.implement === 'object' ? r.implement.faculty : null;
            return fac === userFaculty || !fac;
          })
        );
        setBorrowingsList(
          allBorrowings.filter((b) => {
            const fac = typeof b.implement === 'object' ? b.implement.faculty : null;
            return fac === userFaculty || !fac;
          })
        );
      } else {
        setImplementsList(allImplements);
        setReservesList(allReserves);
        setBorrowingsList(allBorrowings);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cargar inventario.');
    } finally {
      setLoading(false);
    }
  }, [token, isSystemAdmin, userFaculty]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Filtro de implementos
  const filteredImplements = useMemo(() => {
    return implementsList.filter((item) => {
      if (search.trim()) {
        const q = search.toLowerCase();
        const match = item.name.toLowerCase().includes(q) || item.description.toLowerCase().includes(q);
        if (!match) return false;
      }
      if (categoryFilter && item.category !== categoryFilter) return false;
      if (facultyFilter && item.faculty !== facultyFilter) return false;
      if (stateFilter && item.state !== stateFilter) return false;
      return true;
    });
  }, [implementsList, search, categoryFilter, facultyFilter, stateFilter]);

  const paginatedImplements = useMemo(() => {
    const start = page * rowsPerPage;
    return filteredImplements.slice(start, start + rowsPerPage);
  }, [filteredImplements, page, rowsPerPage]);

  // Acciones CRUD implementos
  const handleOpenCreate = () => {
    setImplementToEdit(null);
    setFormModalOpen(true);
  };

  const handleOpenEdit = (item: ImplementItem) => {
    setImplementToEdit(item);
    setFormModalOpen(true);
  };

  const handleFormSubmit = async (payload: CreateImplementPayload | UpdateImplementPayload) => {
    if (!token) return;
    setSavingImplement(true);
    try {
      if (implementToEdit) {
        await updateImplement(implementToEdit.id, payload as UpdateImplementPayload, token);
      } else {
        await createImplement(payload as CreateImplementPayload, token);
      }
      setFormModalOpen(false);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al guardar implemento');
    } finally {
      setSavingImplement(false);
    }
  };

  const handleOpenDelete = (item: ImplementItem) => {
    setItemToDelete(item);
    setDeleteDialogOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (!token || !itemToDelete) return;
    setDeleting(true);
    try {
      await deleteImplement(itemToDelete.id, token);
      setDeleteDialogOpen(false);
      setItemToDelete(null);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al eliminar implemento');
    } finally {
      setDeleting(false);
    }
  };

  // Acciones Reservas
  const handleConfirmDelivery = async (reserveId: number) => {
    if (!token) return;
    setActionLoadingId(reserveId);
    try {
      await confirmReserve(reserveId, token);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al confirmar la entrega');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleCancelReserve = async (reserveId: number) => {
    if (!token) return;
    setActionLoadingId(reserveId);
    try {
      await cancelReserve(reserveId, token);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al cancelar la reserva');
    } finally {
      setActionLoadingId(null);
    }
  };

  // Acciones Préstamos
  const handleReturnBorrowing = async (borrowingId: number) => {
    if (!token) return;
    setActionLoadingId(borrowingId);
    try {
      await returnBorrowing(borrowingId, token);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al registrar la devolución');
    } finally {
      setActionLoadingId(null);
    }
  };

  const stateColorMap = {
    DIS: 'bg-[#00D592]/20 text-[#00875A] border border-[#00D592]/30 dark:text-[#00F5A0]',
    RES: 'bg-amber-500/20 text-amber-800 border border-amber-500/30 dark:text-amber-300',
    PRE: 'bg-blue-500/20 text-blue-800 border border-blue-500/30 dark:text-blue-300',
    NDS: 'bg-gray-100 text-gray-700 border border-gray-200 dark:bg-gray-800 dark:text-gray-300',
  };

  return (
    <div className="space-y-6 font-sans">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <span className="inline-flex items-center rounded-full bg-[#00D592]/15 px-3 py-1 text-xs font-bold text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/30 mb-2">
            Gestión de Inventario
          </span>
          <h1 className="font-display font-extrabold text-2xl sm:text-3xl text-gray-900 dark:text-white">
            {isSystemAdmin ? 'Inventario General del Campus' : `Gestión de Implementos · ${userFaculty ? FACULTY_LABELS[userFaculty] : 'Mi Facultad'}`}
          </h1>
          <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
            Control de material deportivo y recreativo, confirmación de entregas y devoluciones.
          </p>
        </div>

        {activeTab === 'implements' && (
          <button
            type="button"
            onClick={handleOpenCreate}
            className="heroui-btn-solid inline-flex items-center justify-center gap-2 px-5 py-2.5 text-xs font-bold shadow-md w-full sm:w-auto"
          >
            <span className="material-symbols-outlined text-lg" aria-hidden="true">
              add
            </span>
            Nuevo Implemento
          </button>
        )}
      </div>

      {error && !formModalOpen && (
        <ErrorAlert
          title="Error en el panel de inventario"
          message={error}
          onRetry={loadData}
        />
      )}

      {/* Tabs list responsivo (horizontal scrollable en móvil) */}
      <div className="border-b border-[#00D592]/15 dark:border-gray-800 pb-px">
        <nav className="flex overflow-x-auto no-scrollbar gap-2 sm:gap-4 py-1" aria-label="Pestañas de inventario" role="tablist">
          <button
            type="button"
            role="tab"
            id="tab-implements"
            aria-controls="panel-implements"
            aria-selected={activeTab === 'implements'}
            onClick={() => setActiveTab('implements')}
            className={`relative whitespace-nowrap flex items-center gap-2 rounded-full px-4 py-2 text-xs sm:text-sm font-bold transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592] ${
              activeTab === 'implements'
                ? 'text-[#032B1D] dark:text-[#0A1016]'
                : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-[#111A24]'
            }`}
          >
            {activeTab === 'implements' && (
              <motion.div
                layoutId="activeAdminTab"
                className="absolute inset-0 bg-[#00D592] rounded-full shadow-sm"
                transition={{ type: 'spring', bounce: 0.1, duration: 0.4 }}
              />
            )}
            <span className="relative z-10 flex items-center gap-2">
              <span className="material-symbols-outlined text-base" aria-hidden="true">
                inventory_2
              </span>
              Catálogo ({implementsList.length})
            </span>
          </button>

          <button
            type="button"
            role="tab"
            id="tab-reserves"
            aria-controls="panel-reserves"
            aria-selected={activeTab === 'reserves'}
            onClick={() => setActiveTab('reserves')}
            className={`relative whitespace-nowrap flex items-center gap-2 rounded-full px-4 py-2 text-xs sm:text-sm font-bold transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592] ${
              activeTab === 'reserves'
                ? 'text-[#032B1D] dark:text-[#0A1016]'
                : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-[#111A24]'
            }`}
          >
            {activeTab === 'reserves' && (
              <motion.div
                layoutId="activeAdminTab"
                className="absolute inset-0 bg-[#00D592] rounded-full shadow-sm"
                transition={{ type: 'spring', bounce: 0.1, duration: 0.4 }}
              />
            )}
            <span className="relative z-10 flex items-center gap-2">
              <span className="material-symbols-outlined text-base" aria-hidden="true">
                bookmark
              </span>
              Reservas Pendientes ({reservesList.filter((r) => r.active).length})
            </span>
          </button>

          <button
            type="button"
            role="tab"
            id="tab-borrowings"
            aria-controls="panel-borrowings"
            aria-selected={activeTab === 'borrowings'}
            onClick={() => setActiveTab('borrowings')}
            className={`relative whitespace-nowrap flex items-center gap-2 rounded-full px-4 py-2 text-xs sm:text-sm font-bold transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592] ${
              activeTab === 'borrowings'
                ? 'text-[#032B1D] dark:text-[#0A1016]'
                : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-[#111A24]'
            }`}
          >
            {activeTab === 'borrowings' && (
              <motion.div
                layoutId="activeAdminTab"
                className="absolute inset-0 bg-[#00D592] rounded-full shadow-sm"
                transition={{ type: 'spring', bounce: 0.1, duration: 0.4 }}
              />
            )}
            <span className="relative z-10 flex items-center gap-2">
              <span className="material-symbols-outlined text-base" aria-hidden="true">
                handshake
              </span>
              Préstamos Activos ({borrowingsList.filter((b) => b.active).length})
            </span>
          </button>
        </nav>
      </div>

      {loading ? (
        <div className="py-16">
          <LoadingSpinner label="Consultando base de inventario…" />
        </div>
      ) : (
        <AnimatePresence mode="wait">
          {/* TAB 1: IMPLEMENTOS */}
          {activeTab === 'implements' && (
            <motion.div
              key="implements"
              variants={tabContentVariants}
              initial="initial"
              animate="animate"
              exit="exit"
              id="panel-implements"
              role="tabpanel"
              aria-labelledby="tab-implements"
              className="space-y-4"
            >
              {/* Barra de Filtros Responsiva */}
              <div className="heroui-card p-4 sm:p-5 rounded-3xl space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 items-center">
                  {/* Buscador */}
                  <div className="relative col-span-1 sm:col-span-2 lg:col-span-1">
                    <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-[#00D592]" aria-hidden="true">
                      <span className="material-symbols-outlined text-lg">search</span>
                    </span>
                    <input
                      type="search"
                      value={search}
                      onChange={(e) => {
                        setSearch(e.target.value);
                        setPage(0);
                      }}
                      placeholder="Buscar implemento…"
                      className="w-full rounded-2xl border border-gray-200/90 bg-white/70 py-2 pr-3 pl-9.5 text-xs text-gray-900 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-white transition-all"
                    />
                  </div>

                  {/* Select Categoría */}
                  <select
                    value={categoryFilter}
                    onChange={(e) => {
                      setCategoryFilter(e.target.value);
                      setPage(0);
                    }}
                    className="w-full rounded-2xl border border-gray-200/90 bg-white/70 px-3 py-2 text-xs text-gray-900 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-gray-200"
                  >
                    <option value="">Todas las categorías…</option>
                    {Object.entries(IMPLEMENT_CATEGORY_LABELS).map(([k, label]) => {
                      if (!k) return null;
                      return (
                        <option key={k} value={k}>
                          {label}
                        </option>
                      );
                    })}
                  </select>

                  {/* Select Facultad (solo para SystemAdmin) */}
                  {isSystemAdmin ? (
                    <select
                      value={facultyFilter}
                      onChange={(e) => {
                        setFacultyFilter(e.target.value);
                        setPage(0);
                      }}
                      className="w-full rounded-2xl border border-gray-200/90 bg-white/70 px-3 py-2 text-xs text-gray-900 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-gray-200"
                    >
                      <option value="">Todas las facultades…</option>
                      {Object.entries(FACULTY_LABELS).map(([k, label]) => (
                        <option key={k} value={k}>
                          {label}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <div className="hidden lg:block"></div>
                  )}

                  {/* Select Estado */}
                  <select
                    value={stateFilter}
                    onChange={(e) => {
                      setStateFilter(e.target.value);
                      setPage(0);
                    }}
                    className="w-full rounded-2xl border border-gray-200/90 bg-white/70 px-3 py-2 text-xs text-gray-900 focus:border-[#00D592] focus:ring-2 focus:ring-[#00D592]/20 dark:border-gray-800 dark:bg-[#0E1720]/80 dark:text-gray-200"
                  >
                    <option value="">Todos los estados…</option>
                    {Object.entries(IMPLEMENT_STATE_LABELS).map(([k, label]) => (
                      <option key={k} value={k}>
                        {label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* VISTA MÓVIL IMPLEMENTOS (MD:HIDDEN) */}
              <div className="grid grid-cols-1 gap-3.5 md:hidden">
                {paginatedImplements.length === 0 ? (
                  <div className="heroui-card p-6 text-center text-gray-500">
                    No se encontraron implementos con los criterios indicados.
                  </div>
                ) : (
                  paginatedImplements.map((item) => (
                    <div key={item.id} className="heroui-card p-4 space-y-3">
                      <div className="flex items-start gap-3">
                        <div className="h-11 w-11 shrink-0 overflow-hidden rounded-2xl bg-gray-100 dark:bg-gray-800 border border-[#00D592]/20">
                          {item.image ? (
                            <img src={item.image} alt="" aria-hidden="true" className="h-full w-full object-cover" />
                          ) : (
                            <div className="flex h-full w-full items-center justify-center text-[#00BA7C]">
                              <span className="material-symbols-outlined text-xl">sports_tennis</span>
                            </div>
                          )}
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center justify-between gap-1">
                            <span className="heroui-chip text-[10px] py-0.5 px-2">
                              {item.category ? IMPLEMENT_CATEGORY_LABELS[item.category] : 'Sin categoría'}
                            </span>
                            <span className={`inline-block rounded-full px-2 py-0.5 font-bold text-[10px] ${stateColorMap[item.state] || 'bg-gray-100'}`}>
                              {IMPLEMENT_STATE_LABELS[item.state] || item.state}
                            </span>
                          </div>
                          <h3 className="mt-1 font-display font-bold text-sm text-gray-900 dark:text-white truncate">
                            {item.name}
                          </h3>
                        </div>
                      </div>

                      <div className="border-y border-[#00D592]/10 py-2 text-xs text-gray-500 dark:text-gray-400 space-y-1">
                        <p className="line-clamp-2">{item.description}</p>
                        {isSystemAdmin && (
                          <p className="font-semibold text-gray-700 dark:text-gray-300">
                            Facultad: {FACULTY_LABELS[item.faculty] || item.faculty}
                          </p>
                        )}
                      </div>

                      <div className="flex items-center justify-end gap-2 pt-1">
                        <button
                          type="button"
                          onClick={() => handleOpenEdit(item)}
                          className="heroui-btn-flat px-3.5 py-1.5 text-xs font-bold inline-flex items-center gap-1"
                        >
                          <span className="material-symbols-outlined text-sm">edit</span>
                          <span>Editar</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => handleOpenDelete(item)}
                          className="rounded-full bg-red-500/10 text-red-600 border border-red-500/20 px-3 py-1.5 text-xs font-bold inline-flex items-center gap-1 hover:bg-red-500/20 transition-colors"
                        >
                          <span className="material-symbols-outlined text-sm">delete</span>
                          <span>Eliminar</span>
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>

              {/* VISTA ESCRITORIO IMPLEMENTOS (HIDDEN MD:BLOCK) */}
              <div className="hidden md:block overflow-x-auto heroui-card p-0 overflow-hidden">
                <table className="min-w-full divide-y divide-gray-200 text-left text-xs dark:divide-gray-800">
                  <thead className="bg-gray-50/80 text-gray-600 dark:bg-[#111A24] dark:text-gray-300">
                    <tr>
                      <th scope="col" className="px-4 py-3.5 font-bold">
                        Implemento
                      </th>
                      <th scope="col" className="px-3 py-3.5 font-bold">
                        Categoría
                      </th>
                      {isSystemAdmin && (
                        <th scope="col" className="px-3 py-3.5 font-bold">
                          Facultad
                        </th>
                      )}
                      <th scope="col" className="px-3 py-3.5 font-bold">
                        Estado
                      </th>
                      <th scope="col" className="px-4 py-3.5 text-right font-bold">
                        Acciones
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-800/80">
                    {paginatedImplements.length === 0 ? (
                      <tr>
                        <td colSpan={isSystemAdmin ? 5 : 4} className="px-4 py-8 text-center text-gray-500">
                          No se encontraron implementos con los criterios indicados.
                        </td>
                      </tr>
                    ) : (
                      paginatedImplements.map((item) => (
                        <tr key={item.id} className="hover:bg-gray-50/70 dark:hover:bg-[#14202C]/50 transition-colors">
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-3">
                              <div className="h-9 w-9 shrink-0 overflow-hidden rounded-xl bg-gray-100 dark:bg-gray-800 border border-[#00D592]/20">
                                {item.image ? (
                                  <img src={item.image} alt="" aria-hidden="true" className="h-full w-full object-cover" />
                                ) : (
                                  <div className="flex h-full w-full items-center justify-center text-[#00BA7C]">
                                    <span className="material-symbols-outlined text-base">sports_tennis</span>
                                  </div>
                                )}
                              </div>
                              <div className="min-w-0 max-w-xs">
                                <p className="font-bold text-sm text-gray-900 dark:text-white truncate">
                                  {item.name}
                                </p>
                                <p className="text-[11px] text-gray-500 line-clamp-1">{item.description}</p>
                              </div>
                            </div>
                          </td>

                          <td className="px-3 py-3 text-gray-600 dark:text-gray-300 font-medium">
                            {item.category ? IMPLEMENT_CATEGORY_LABELS[item.category] : 'Sin categoría'}
                          </td>

                          {isSystemAdmin && (
                            <td className="px-3 py-3 text-gray-600 dark:text-gray-300 font-medium">
                              {FACULTY_LABELS[item.faculty] || item.faculty}
                            </td>
                          )}

                          <td className="px-3 py-3">
                            <span className={`inline-block rounded-full px-2.5 py-0.5 font-bold text-[10px] ${stateColorMap[item.state] || 'bg-gray-100'}`}>
                              {IMPLEMENT_STATE_LABELS[item.state] || item.state}
                            </span>
                          </td>

                          <td className="px-4 py-3 text-right">
                            <div className="flex items-center justify-end gap-1.5">
                              <button
                                type="button"
                                onClick={() => handleOpenEdit(item)}
                                aria-label={`Editar ${item.name}`}
                                className="rounded-full p-1.5 text-gray-600 hover:bg-[#00D592]/15 hover:text-[#00BA7C] focus-visible:outline-2 focus-visible:outline-[#00D592] dark:text-gray-400 dark:hover:bg-gray-800 transition-colors"
                              >
                                <span className="material-symbols-outlined text-lg">edit</span>
                              </button>
                              <button
                                type="button"
                                onClick={() => handleOpenDelete(item)}
                                aria-label={`Eliminar ${item.name}`}
                                className="rounded-full p-1.5 text-red-500 hover:bg-red-50 hover:text-red-700 focus-visible:outline-2 focus-visible:outline-[#00D592] dark:text-red-400 dark:hover:bg-red-950/40 transition-colors"
                              >
                                <span className="material-symbols-outlined text-lg">delete</span>
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <Pagination
                count={filteredImplements.length}
                page={page}
                rowsPerPage={rowsPerPage}
                onPageChange={setPage}
                onRowsPerPageChange={(nr) => {
                  setRowsPerPage(nr);
                  setPage(0);
                }}
              />
            </motion.div>
          )}

          {/* TAB 2: RESERVAS */}
          {activeTab === 'reserves' && (
            <motion.div
              key="reserves"
              variants={tabContentVariants}
              initial="initial"
              animate="animate"
              exit="exit"
              id="panel-reserves"
              role="tabpanel"
              aria-labelledby="tab-reserves"
              className="space-y-4"
            >
              {/* VISTA MÓVIL RESERVAS (MD:HIDDEN) */}
              <div className="grid grid-cols-1 gap-3.5 md:hidden">
                {reservesList.length === 0 ? (
                  <div className="heroui-card p-6 text-center text-gray-500">
                    No hay reservas registradas.
                  </div>
                ) : (
                  reservesList.map((res) => {
                    const impName = typeof res.implement === 'object' ? res.implement.name : `Implemento #${res.implement}`;
                    const impFac = typeof res.implement === 'object' ? FACULTY_LABELS[res.implement.faculty] : '';

                    const reservedTime = new Intl.DateTimeFormat('es-CO', {
                      day: 'numeric',
                      month: 'short',
                      hour: '2-digit',
                      minute: '2-digit',
                    }).format(new Date(res.datetime_reserved));

                    const expirationTime = new Intl.DateTimeFormat('es-CO', {
                      hour: '2-digit',
                      minute: '2-digit',
                    }).format(new Date(res.datetime_expiration));

                    const isProcessing = actionLoadingId === res.id;

                    return (
                      <div key={res.id} className="heroui-card p-4 space-y-3">
                        <div className="flex items-start justify-between gap-2">
                          <div>
                            <span className="text-[10px] font-bold text-[#00875A] dark:text-[#00F5A0] uppercase">
                              {impFac || 'Reserva UNAL'}
                            </span>
                            <h3 className="font-display font-bold text-sm text-gray-900 dark:text-white">
                              {impName}
                            </h3>
                          </div>
                          {res.active ? (
                            <span className="rounded-full bg-amber-500/20 text-amber-800 dark:text-amber-300 px-2.5 py-0.5 font-bold text-[10px] border border-amber-500/30">
                              Activa
                            </span>
                          ) : (
                            <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[10px] text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                              Finalizada
                            </span>
                          )}
                        </div>

                        <div className="border-y border-[#00D592]/10 py-2 text-xs space-y-1">
                          <p className="font-semibold text-gray-900 dark:text-white">
                            {res.user.name} <span className="text-gray-500 font-normal">({res.user.email})</span>
                          </p>
                          <div className="flex flex-wrap items-center gap-3 text-gray-600 dark:text-gray-400">
                            <span>Reservado: {reservedTime}</span>
                            <span className="font-bold text-amber-600 dark:text-amber-400">Expira: {expirationTime}</span>
                          </div>
                        </div>

                        {res.active && (
                          <div className="flex items-center justify-end gap-2 pt-1">
                            <button
                              type="button"
                              disabled={isProcessing}
                              onClick={() => handleConfirmDelivery(res.id)}
                              className="heroui-btn-solid px-3.5 py-1.5 text-xs font-bold disabled:opacity-50"
                            >
                              {isProcessing ? 'Confirmando…' : 'Confirmar entrega'}
                            </button>
                            <button
                              type="button"
                              disabled={isProcessing}
                              onClick={() => handleCancelReserve(res.id)}
                              className="rounded-full bg-red-500/10 text-red-600 border border-red-500/20 px-3 py-1.5 text-xs font-bold disabled:opacity-50"
                            >
                              Cancelar
                            </button>
                          </div>
                        )}
                      </div>
                    );
                  })
                )}
              </div>

              {/* VISTA ESCRITORIO RESERVAS (HIDDEN MD:BLOCK) */}
              <div className="hidden md:block overflow-x-auto heroui-card p-0 overflow-hidden">
                <table className="min-w-full divide-y divide-gray-200 text-left text-xs dark:divide-gray-800">
                  <thead className="bg-gray-50/80 text-gray-600 dark:bg-[#111A24] dark:text-gray-300">
                    <tr>
                      <th scope="col" className="px-4 py-3.5 font-bold">
                        Implemento
                      </th>
                      <th scope="col" className="px-3 py-3.5 font-bold">
                        Usuario UNAL
                      </th>
                      <th scope="col" className="px-3 py-3.5 font-bold">
                        Reservado el
                      </th>
                      <th scope="col" className="px-3 py-3.5 font-bold">
                        Expira a las
                      </th>
                      <th scope="col" className="px-3 py-3.5 font-bold">
                        Estado
                      </th>
                      <th scope="col" className="px-4 py-3.5 text-right font-bold">
                        Acciones
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-800/80">
                    {reservesList.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="px-4 py-8 text-center text-gray-500">
                          No hay reservas registradas.
                        </td>
                      </tr>
                    ) : (
                      reservesList.map((res) => {
                        const impName = typeof res.implement === 'object' ? res.implement.name : `Implemento #${res.implement}`;
                        const impFac = typeof res.implement === 'object' ? FACULTY_LABELS[res.implement.faculty] : '';

                        const reservedTime = new Intl.DateTimeFormat('es-CO', {
                          day: 'numeric',
                          month: 'short',
                          hour: '2-digit',
                          minute: '2-digit',
                        }).format(new Date(res.datetime_reserved));

                        const expirationTime = new Intl.DateTimeFormat('es-CO', {
                          hour: '2-digit',
                          minute: '2-digit',
                        }).format(new Date(res.datetime_expiration));

                        const isProcessing = actionLoadingId === res.id;

                        return (
                          <tr key={res.id} className="hover:bg-gray-50/70 dark:hover:bg-[#14202C]/50 transition-colors">
                            <td className="px-4 py-3">
                              <p className="font-bold text-sm text-gray-900 dark:text-white">{impName}</p>
                              {impFac && <p className="text-[11px] text-gray-500">{impFac}</p>}
                            </td>

                            <td className="px-3 py-3">
                              <p className="font-semibold text-gray-900 dark:text-white">{res.user.name}</p>
                              <p className="text-[11px] text-gray-500">{res.user.email}</p>
                            </td>

                            <td className="px-3 py-3 tabular-nums text-gray-600 dark:text-gray-300">
                              {reservedTime}
                            </td>

                            <td className="px-3 py-3 tabular-nums font-bold text-amber-600 dark:text-amber-400">
                              {expirationTime}
                            </td>

                            <td className="px-3 py-3">
                              {res.active ? (
                                <span className="inline-block rounded-full bg-amber-500/20 text-amber-800 dark:text-amber-300 px-2.5 py-0.5 font-bold text-[10px] border border-amber-500/30">
                                  Activa
                                </span>
                              ) : (
                                <span className="inline-block rounded-full bg-gray-100 px-2.5 py-0.5 text-[10px] text-gray-600">
                                  Finalizada
                                </span>
                              )}
                            </td>

                            <td className="px-4 py-3 text-right">
                              {res.active && (
                                <div className="flex items-center justify-end gap-2">
                                  <button
                                    type="button"
                                    disabled={isProcessing}
                                    onClick={() => handleConfirmDelivery(res.id)}
                                    className="heroui-btn-solid px-3 py-1 text-xs font-bold disabled:opacity-50"
                                  >
                                    {isProcessing ? 'Confirmando…' : 'Confirmar entrega'}
                                  </button>
                                  <button
                                    type="button"
                                    disabled={isProcessing}
                                    onClick={() => handleCancelReserve(res.id)}
                                    className="rounded-full bg-red-500/10 text-red-600 border border-red-500/20 px-3 py-1 text-xs font-bold disabled:opacity-50 hover:bg-red-500/20 transition-colors"
                                  >
                                    Cancelar
                                  </button>
                                </div>
                              )}
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </motion.div>
          )}

          {/* TAB 3: PRÉSTAMOS */}
          {activeTab === 'borrowings' && (
            <motion.div
              key="borrowings"
              variants={tabContentVariants}
              initial="initial"
              animate="animate"
              exit="exit"
              id="panel-borrowings"
              role="tabpanel"
              aria-labelledby="tab-borrowings"
              className="space-y-4"
            >
              {/* VISTA MÓVIL PRÉSTAMOS (MD:HIDDEN) */}
              <div className="grid grid-cols-1 gap-3.5 md:hidden">
                {borrowingsList.length === 0 ? (
                  <div className="heroui-card p-6 text-center text-gray-500">
                    No hay préstamos registrados.
                  </div>
                ) : (
                  borrowingsList.map((b) => {
                    const impName = typeof b.implement === 'object' ? b.implement.name : `Implemento #${b.implement}`;
                    const impFac = typeof b.implement === 'object' ? FACULTY_LABELS[b.implement.faculty] : '';

                    const borrowedTime = new Intl.DateTimeFormat('es-CO', {
                      day: 'numeric',
                      month: 'short',
                      hour: '2-digit',
                      minute: '2-digit',
                    }).format(new Date(b.datetime_borrowed));

                    const isProcessing = actionLoadingId === b.id;

                    return (
                      <div key={b.id} className="heroui-card p-4 space-y-3">
                        <div className="flex items-start justify-between gap-2">
                          <div>
                            <span className="text-[10px] font-bold text-[#00875A] dark:text-[#00F5A0] uppercase">
                              {impFac || 'Préstamo UNAL'}
                            </span>
                            <h3 className="font-display font-bold text-sm text-gray-900 dark:text-white">
                              {impName}
                            </h3>
                          </div>
                          {b.active ? (
                            <span className="rounded-full bg-blue-500/20 text-blue-800 dark:text-blue-300 px-2.5 py-0.5 font-bold text-[10px] border border-blue-500/30">
                              En Préstamo
                            </span>
                          ) : (
                            <span className="rounded-full bg-gray-100 px-2.5 py-0.5 text-[10px] text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                              Devuelto
                            </span>
                          )}
                        </div>

                        <div className="border-y border-[#00D592]/10 py-2 text-xs space-y-1">
                          <p className="font-semibold text-gray-900 dark:text-white">
                            En posesión de: {b.user.name} <span className="text-gray-500 font-normal">({b.user.email})</span>
                          </p>
                          <p className="text-gray-600 dark:text-gray-400">
                            Entregado el: <span className="tabular-nums font-semibold">{borrowedTime}</span>
                          </p>
                        </div>

                        {b.active && (
                          <div className="flex items-center justify-end pt-1">
                            <button
                              type="button"
                              disabled={isProcessing}
                              onClick={() => handleReturnBorrowing(b.id)}
                              className="heroui-btn-solid px-4 py-1.5 text-xs font-bold disabled:opacity-50"
                            >
                              {isProcessing ? 'Registrando…' : 'Registrar devolución'}
                            </button>
                          </div>
                        )}
                      </div>
                    );
                  })
                )}
              </div>

              {/* VISTA ESCRITORIO PRÉSTAMOS (HIDDEN MD:BLOCK) */}
              <div className="hidden md:block overflow-x-auto heroui-card p-0 overflow-hidden">
                <table className="min-w-full divide-y divide-gray-200 text-left text-xs dark:divide-gray-800">
                  <thead className="bg-gray-50/80 text-gray-600 dark:bg-[#111A24] dark:text-gray-300">
                    <tr>
                      <th scope="col" className="px-4 py-3.5 font-bold">
                        Implemento
                      </th>
                      <th scope="col" className="px-3 py-3.5 font-bold">
                        Usuario en Posesión
                      </th>
                      <th scope="col" className="px-3 py-3.5 font-bold">
                        Fecha y Hora Préstamo
                      </th>
                      <th scope="col" className="px-3 py-3.5 font-bold">
                        Estado
                      </th>
                      <th scope="col" className="px-4 py-3.5 text-right font-bold">
                        Acciones
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-800/80">
                    {borrowingsList.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-8 text-center text-gray-500">
                          No hay préstamos registrados.
                        </td>
                      </tr>
                    ) : (
                      borrowingsList.map((b) => {
                        const impName = typeof b.implement === 'object' ? b.implement.name : `Implemento #${b.implement}`;
                        const impFac = typeof b.implement === 'object' ? FACULTY_LABELS[b.implement.faculty] : '';

                        const borrowedTime = new Intl.DateTimeFormat('es-CO', {
                          day: 'numeric',
                          month: 'short',
                          hour: '2-digit',
                          minute: '2-digit',
                        }).format(new Date(b.datetime_borrowed));

                        const isProcessing = actionLoadingId === b.id;

                        return (
                          <tr key={b.id} className="hover:bg-gray-50/70 dark:hover:bg-[#14202C]/50 transition-colors">
                            <td className="px-4 py-3">
                              <p className="font-bold text-sm text-gray-900 dark:text-white">{impName}</p>
                              {impFac && <p className="text-[11px] text-gray-500">{impFac}</p>}
                            </td>

                            <td className="px-3 py-3">
                              <p className="font-semibold text-gray-900 dark:text-white">{b.user.name}</p>
                              <p className="text-[11px] text-gray-500">{b.user.email}</p>
                            </td>

                            <td className="px-3 py-3 tabular-nums text-gray-600 dark:text-gray-300">
                              {borrowedTime}
                            </td>

                            <td className="px-3 py-3">
                              {b.active ? (
                                <span className="inline-block rounded-full bg-blue-500/20 text-blue-800 dark:text-blue-300 px-2.5 py-0.5 font-bold text-[10px] border border-blue-500/30">
                                  En Préstamo
                                </span>
                              ) : (
                                <span className="inline-block rounded-full bg-gray-100 px-2.5 py-0.5 text-[10px] text-gray-600">
                                  Devuelto
                                </span>
                              )}
                            </td>

                            <td className="px-4 py-3 text-right">
                              {b.active && (
                                <button
                                  type="button"
                                  disabled={isProcessing}
                                  onClick={() => handleReturnBorrowing(b.id)}
                                  className="heroui-btn-solid px-3 py-1 text-xs font-bold disabled:opacity-50"
                                >
                                  {isProcessing ? 'Registrando…' : 'Registrar devolución'}
                                </button>
                              )}
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      )}

      {/* Modal de Formulario */}
      <EquipmentFormModal
        open={formModalOpen}
        implementToEdit={implementToEdit}
        isSystemAdmin={isSystemAdmin}
        defaultFaculty={userFaculty}
        onClose={() => setFormModalOpen(false)}
        onSubmit={handleFormSubmit}
        saving={savingImplement}
      />

      {/* Modal de Confirmar Borrado */}
      <ConfirmDialog
        open={deleteDialogOpen}
        title="¿Eliminar este implemento?"
        description={`Se eliminará permanentemente "${itemToDelete?.name}". Se borrarán en cascada las reservas o préstamos históricos asociados a este material.`}
        confirmLabel="Eliminar implemento"
        cancelLabel="Cancelar"
        danger
        pending={deleting}
        onClose={() => setDeleteDialogOpen(false)}
        onConfirm={handleConfirmDelete}
      />
    </div>
  );
}
