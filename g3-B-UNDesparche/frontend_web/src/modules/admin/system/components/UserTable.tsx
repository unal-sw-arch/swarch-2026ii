import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { BackendProfile } from '../../../../types/auth';
import { FACULTY_LABELS } from '../../../../types/equipment';

export interface UserTableProps {
  users: BackendProfile[];
  currentUserId?: number;
  onEdit: (user: BackendProfile) => void;
  onDelete: (user: BackendProfile) => void;
}

export function UserTable({
  users,
  currentUserId,
  onEdit,
  onDelete,
}: UserTableProps): React.JSX.Element {
  if (users.length === 0) {
    return (
      <div className="heroui-card p-8 text-center text-gray-500 dark:text-gray-400">
        <span className="material-symbols-outlined text-4xl text-gray-400 mb-2">person_off</span>
        <p className="font-bold text-sm">No se encontraron usuarios registrados.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* 1. VISTA MÓVIL (TARJETAS MD:HIDDEN) */}
      <div className="grid grid-cols-1 gap-3.5 md:hidden">
        <AnimatePresence mode="popLayout">
          {users.map((u) => {
            const initial = u.name ? u.name.charAt(0).toUpperCase() : 'U';
            const isOtherSystemAdmin =
              u.roles?.includes('Administrador del Sistema') && u.id !== currentUserId;

            return (
              <motion.div
                layout
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ duration: 0.2 }}
                key={u.id}
                className="heroui-card p-4 space-y-3 border border-[#00D592]/15 shadow-sm"
              >
              {/* Usuario Avatar & Nombre */}
              <div className="flex items-center gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-tr from-[#00D592] to-[#00BA7C] font-extrabold text-sm text-[#032B1D] shadow-xs ring-2 ring-[#00D592]/30">
                  {initial}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-1">
                    <p className="font-bold text-sm text-gray-900 dark:text-white truncate">
                      {u.name}
                    </p>
                    {u.status === 'SAN' ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 font-bold text-[10px] text-red-800 dark:bg-red-950/60 dark:text-red-300">
                        <span className="h-1.5 w-1.5 rounded-full bg-red-600" />
                        Sancionado
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-full bg-[#00D592]/20 px-2 py-0.5 font-bold text-[10px] text-[#00875A] dark:text-[#00F5A0]">
                        <span className="h-1.5 w-1.5 rounded-full bg-[#00D592]" />
                        Activo
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-gray-500 dark:text-gray-400 truncate">{u.email}</p>
                </div>
              </div>

              {/* Rol & Facultad */}
              <div className="flex flex-wrap items-center gap-2 border-y border-[#00D592]/10 py-2.5 text-xs">
                <span className="rounded-full bg-[#00D592]/15 px-2.5 py-0.5 font-bold text-[11px] text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/25">
                  {u.roles?.[0] || 'Miembro de la Comunidad'}
                </span>
                <span className="text-gray-500 dark:text-gray-400">
                  {u.faculty && u.faculty in FACULTY_LABELS
                    ? FACULTY_LABELS[u.faculty as keyof typeof FACULTY_LABELS]
                    : u.faculty || 'Sin facultad asignada'}
                </span>
              </div>

              {/* Acciones */}
              <div className="flex items-center justify-end gap-2 pt-1">
                {isOtherSystemAdmin ? (
                  <span className="text-xs text-gray-400 italic">
                    Protegido (Admin Sistema)
                  </span>
                ) : (
                  <>
                    <button
                      type="button"
                      onClick={() => onEdit(u)}
                      className="heroui-btn-flat px-3.5 py-1.5 text-xs font-bold inline-flex items-center gap-1"
                    >
                      <span className="material-symbols-outlined text-sm">manage_accounts</span>
                      <span>Gestionar rol</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => onDelete(u)}
                      className="rounded-full bg-red-500/10 text-red-600 border border-red-500/20 px-3 py-1.5 text-xs font-bold inline-flex items-center gap-1 hover:bg-red-500/20 transition-colors"
                    >
                      <span className="material-symbols-outlined text-sm">person_remove</span>
                      <span>Eliminar</span>
                    </button>
                  </>
                )}
              </div>
            </motion.div>
          );
        })}
        </AnimatePresence>
      </div>

      {/* 2. VISTA ESCRITORIO (TABLA HIDDEN MD:BLOCK) */}
      <div className="hidden md:block overflow-x-auto heroui-card p-0 overflow-hidden shadow-sm">
        <table className="min-w-full divide-y divide-gray-200 text-left text-xs dark:divide-gray-800">
          <thead className="bg-gray-50/80 text-gray-600 dark:bg-[#111A24] dark:text-gray-300">
            <tr>
              <th scope="col" className="px-4 py-3.5 font-bold">
                Usuario
              </th>
              <th scope="col" className="px-3 py-3.5 font-bold">
                Facultad
              </th>
              <th scope="col" className="px-3 py-3.5 font-bold">
                Rol
              </th>
              <th scope="col" className="px-3 py-3.5 font-bold">
                Estado Disciplinario
              </th>
              <th scope="col" className="px-4 py-3.5 text-right font-bold">
                Acciones
              </th>
            </tr>
          </thead>
          <motion.tbody className="divide-y divide-gray-100 dark:divide-gray-800/80">
            <AnimatePresence>
            {users.map((u) => {
              const initial = u.name ? u.name.charAt(0).toUpperCase() : 'U';
              const isOtherSystemAdmin =
                u.roles?.includes('Administrador del Sistema') && u.id !== currentUserId;

              return (
                <motion.tr 
                  layout
                  initial={{ opacity: 0, backgroundColor: 'rgba(0, 213, 146, 0.2)' }}
                  animate={{ opacity: 1, backgroundColor: 'transparent' }}
                  exit={{ opacity: 0, x: -10 }}
                  transition={{ duration: 0.2 }}
                  key={u.id} 
                  className="hover:bg-gray-50/70 dark:hover:bg-[#14202C]/50 transition-colors"
                >
                  {/* Usuario con Avatar */}
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-tr from-[#00D592] to-[#00BA7C] font-bold text-xs text-[#032B1D]">
                        {initial}
                      </div>
                      <div className="min-w-0 max-w-xs">
                        <p className="font-bold text-sm text-gray-900 dark:text-white truncate">
                          {u.name}
                        </p>
                        <p className="text-[11px] text-gray-500 truncate">{u.email}</p>
                      </div>
                    </div>
                  </td>

                  {/* Facultad */}
                  <td className="px-3 py-3 text-xs text-gray-700 dark:text-gray-300">
                    {u.faculty && u.faculty in FACULTY_LABELS
                      ? FACULTY_LABELS[u.faculty as keyof typeof FACULTY_LABELS]
                      : u.faculty || 'Sin asignar'}
                  </td>

                  {/* Rol */}
                  <td className="px-3 py-3">
                    <span className="inline-block rounded-full bg-[#00D592]/15 px-2.5 py-0.5 font-bold text-[10px] text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/30">
                      {u.roles?.[0] || 'Miembro de la Comunidad'}
                    </span>
                  </td>

                  {/* Estado disciplinario */}
                  <td className="px-3 py-3">
                    {u.status === 'SAN' ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2.5 py-0.5 font-bold text-[10px] text-red-800 dark:bg-red-950/60 dark:text-red-300">
                        <span className="h-1.5 w-1.5 rounded-full bg-red-600" />
                        Sancionado (SAN)
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-full bg-[#00D592]/20 px-2.5 py-0.5 font-bold text-[10px] text-[#00875A] dark:text-[#00F5A0]">
                        <span className="h-1.5 w-1.5 rounded-full bg-[#00D592]" />
                        Activo (ACT)
                      </span>
                    )}
                  </td>

                  {/* Acciones */}
                  <td className="px-4 py-3 text-right">
                    {isOtherSystemAdmin ? (
                      <span className="text-[10px] text-gray-400 italic">
                        Protegido (Admin Sistema)
                      </span>
                    ) : (
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => onEdit(u)}
                          aria-label={`Editar rol y estado de ${u.name}`}
                          className="rounded-full p-1.5 text-gray-600 hover:bg-[#00D592]/15 hover:text-[#00BA7C] focus-visible:outline-2 focus-visible:outline-[#00D592] dark:text-gray-400 dark:hover:bg-gray-800 transition-colors"
                        >
                          <span className="material-symbols-outlined text-lg" aria-hidden="true">
                            manage_accounts
                          </span>
                        </button>
                        <button
                          type="button"
                          onClick={() => onDelete(u)}
                          aria-label={`Eliminar usuario ${u.name}`}
                          className="rounded-full p-1.5 text-red-500 hover:bg-red-50 hover:text-red-700 focus-visible:outline-2 focus-visible:outline-[#00D592] dark:text-red-400 dark:hover:bg-red-950/40 transition-colors"
                        >
                          <span className="material-symbols-outlined text-lg" aria-hidden="true">
                            person_remove
                          </span>
                        </button>
                      </div>
                    )}
                  </td>
                </motion.tr>
              );
            })}
            </AnimatePresence>
          </motion.tbody>
        </table>
      </div>
    </div>
  );
}
