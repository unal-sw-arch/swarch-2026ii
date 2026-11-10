// El login guarda { message, user: {...} }; algunos flujos guardan el usuario directo.
export const unwrapUser = (user) => user?.user || user || null;

export const getRole = (user) => unwrapUser(user)?.role || 'Usuario';
export const isStaff = (user) => ['Comedor', 'Administrador'].includes(getRole(user));

// Página de inicio según el rol
export const homeFor = (user) => (getRole(user) === 'Comedor' ? '/comedor' : (getRole(user)==='Administrador'? '/admin':'/mis-pedidos'));
