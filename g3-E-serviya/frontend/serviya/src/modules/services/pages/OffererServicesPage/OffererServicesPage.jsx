import { useForm } from 'react-hook-form';
import { useState, useEffect } from "react";
import { useNavigate } from 'react-router-dom';
import { DashboardLayout, Icon, Modal, ToastContainer, useToast, OFFERER_NAV, serviceApi, profileApi, categoryApi, getApiImageUrl, isAuthenticated, ServiceImage } from '../../../../shared';

import './OffererServicesPage.css';

function ServiceModal({
    onClose,
    title,
    sub,
    onSave,
    edit,
    categories,
    editedService,
    selectedPhotos,
    setSelectedPhotos,
    removePhoto
}) {

    const {
        register,
        handleSubmit,
        reset
    } = useForm({
        defaultValues: editedService || {}
    });
    const [previewPhoto, setPreviewPhoto] = useState(null);

    useEffect(() => {
        if (!editedService) {
            setSelectedPhotos([]);
            return;
        }
        setSelectedPhotos(editedService.photos || []);
    }, [editedService, setSelectedPhotos]);

    useEffect(() => {
        if (!editedService) return;

        reset(editedService);
    }, [editedService, reset]);

    return (
        <Modal open={true} onClose={onClose} maxWidth={520}>
            <form onSubmit={handleSubmit(onSave)}>
                <div className="modal-title">{title}</div>
                <div className="modal-sub">{sub}</div>
                <div className="input-group">
                    <label className="label">
                        Título del servicio
                    </label>
                    <input
                        className="input"
                        placeholder="ej: Reparación de tuberías"
                        {...register("title", {
                            required: true
                        })}
                    />
                </div>
                <div className="g2">
                    <div className="input-group">
                        <label className="label">
                            Categoría
                        </label>
                        <select
                            className="input"
                            {...register("categoryId", {
                                required: true,
                                valueAsNumber: true
                            })}
                        >
                            <option value="">
                                Seleccione una categoría
                            </option>
                            {categories.map(category => (
                                <option
                                    key={category.id}
                                    value={category.id}
                                >
                                    {category.name}
                                </option>
                            ))}
                        </select>
                    </div>
                    <div className="input-group">
                        <label className="label">
                            Precio / hora (COP)
                        </label>
                        <input
                            className="input"
                            type="number"
                            {...register("priceHourly", {
                                valueAsNumber: true
                            })}
                        />
                    </div>
                </div>
                <div className="g2">
                    <div className="input-group">
                        <label className="label">
                            Duración estimada (min)
                        </label>
                        <input
                            className="input"
                            type="number"
                            {...register(
                                "averageDurationMinutes",
                                {
                                    valueAsNumber: true
                                }
                            )}
                        />
                    </div>
                    <div className="input-group">
                        <label className="label">
                            Radio operación (km)
                        </label>
                        <input
                            className="input"
                            type="number"
                            {...register(
                                "operationRadiusKm",
                                {
                                    valueAsNumber: true
                                }
                            )}
                        />
                    </div>
                </div>
                <div className="input-group">
                    <label className="label">
                        Descripción
                    </label>
                    <textarea
                        className="input"
                        rows="3"
                        {...register("description")}
                    />
                </div>
                <div className="input-group">
                    <label className="label">Fotos del servicio (máx. 15)</label>
                    <input
                        className="input"
                        type="file"
                        accept="image/*"
                        multiple
                        onChange={(e) => {
                            const files = Array.from(e.target.files || []);
                            if (!files.length) return;
                            setSelectedPhotos(prev => [...prev, ...files]);
                            e.target.value = '';
                        }}
                    />
                    {selectedPhotos.length > 0 && (
                        <div style={{ marginTop: '8px', display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                            {selectedPhotos.map((photo, index) => {
                                const src = photo instanceof File ? URL.createObjectURL(photo) : getApiImageUrl(photo);
                                return (
                                    <div key={`${index}-${typeof photo === 'string' ? photo : photo.name}`} style={{ position: 'relative', cursor: 'pointer' }}>
                                        <button
                                            type="button"
                                            onClick={(event) => {
                                                event.stopPropagation();
                                                removePhoto(index);
                                            }}
                                            style={{
                                                position: 'absolute',
                                                top: '4px',
                                                right: '4px',
                                                width: '20px',
                                                height: '20px',
                                                border: 'none',
                                                borderRadius: '50%',
                                                background: '#dc2626',
                                                color: '#fff',
                                                fontSize: '12px',
                                                lineHeight: '1',
                                                cursor: 'pointer',
                                                boxShadow: '0 0 0 2px rgba(255,255,255,0.8)'
                                            }}
                                            aria-label="Eliminar foto"
                                        >
                                            ×
                                        </button>
                                        <ServiceImage src={src} alt={`Foto ${index + 1}`} onClick={() => setPreviewPhoto(src)} style={{ width: '72px', height: '72px', objectFit: 'cover', borderRadius: '8px', border: '1px solid var(--c-border)' }} />
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
                <div
                    style={{
                        display: 'flex',
                        gap: '8px'
                    }}
                >
                    <button
                        type="button"
                        className="btn btn-ghost btn-full"
                        onClick={onClose}>
                        Cancelar
                    </button>
                    <button
                        type="submit"
                        className="btn btn-primary btn-full">
                        {edit
                            ? 'Guardar cambios'
                            : 'Crear servicio'}
                    </button>
                </div>
            </form>
            {previewPhoto && (
                <Modal open={true} onClose={() => setPreviewPhoto(null)} maxWidth={760}>
                    <div style={{ textAlign: 'center' }}>
                        <ServiceImage src={previewPhoto} alt="Vista previa" style={{ maxWidth: '100%', maxHeight: '70vh', objectFit: 'contain', borderRadius: '12px' }} />
                    </div>
                </Modal>
            )}
        </Modal>
    );
}

export function OffererServicesPage() {
    const navigate = useNavigate();
    const { toasts, showToast } = useToast();
    const [newOpen, setNewOpen] = useState(false);
    const [editOpen, setEditOpen] = useState(false);
    const [profile, setProfile] = useState(null);
    const [services, setServices] = useState([]);
    const [categories, setCategories] = useState([]);
    const [editedService, setEditedService] = useState(null);
    const [selectedPhotos, setSelectedPhotos] = useState([]);

    useEffect(() => {
        if (!isAuthenticated()) {
            navigate('/login');
            return;
        }
        profileApi.getMyProfile()
            .then(setProfile)
            .catch((e) => showToast(e.message || 'No se pudo cargar tu perfil', 'danger'));
        categoryApi.getCategories()
            .then(setCategories)
            .catch((e) => showToast(e.message || 'No se pudieron cargar las categorías', 'danger'));
    }, []);

    useEffect(() => {
        if (!profile?.id) return;
        serviceApi.getMyServices(profile.id)
            .then(setServices)
            .catch(() => setServices([]));
    }, [profile?.id]);

    const openEdit = (service) => () => {
        setEditedService({
            ...service,
            categoryId: service.category?.id
        });
        setEditOpen(true);
    }

    const closeModal = () => {
        setNewOpen(false);
        setEditOpen(false);
        setEditedService(null);
        setSelectedPhotos([]);
    };

    const normalizeNumericValue = (value) => {
        if (value === null || value === undefined || value === '' || value === 'NaN') return null;
        const numericValue = typeof value === 'number' ? value : Number(value);
        return Number.isFinite(numericValue) ? numericValue : null;
    };

    const createService = (formData) => {
        const categoryId = normalizeNumericValue(formData.categoryId);
        if (categoryId === null) {
            showToast('Selecciona una categoría antes de crear el servicio', 'danger');
            return;
        }

        const category = categories.find(c => c.id === categoryId);
        const formDataToSend = new FormData();
        formDataToSend.append('title', formData.title || '');
        formDataToSend.append('description', formData.description || '');
        const priceHourly = normalizeNumericValue(formData.priceHourly);
        formDataToSend.append('priceHourly', String(priceHourly ?? 0));
        formDataToSend.append('categoryId', String(categoryId));

        const averageDurationMinutes = normalizeNumericValue(formData.averageDurationMinutes);
        if (averageDurationMinutes !== null) formDataToSend.append('averageDurationMinutes', String(averageDurationMinutes));

        const operationRadiusKm = normalizeNumericValue(formData.operationRadiusKm);
        if (operationRadiusKm !== null) formDataToSend.append('operationRadiusKm', String(operationRadiusKm));

        selectedPhotos.forEach((photo) => formDataToSend.append('photos', photo));

        serviceApi
            .createService(formDataToSend, true)
            .then((created) => {
                setServices(prev => [
                    ...prev,
                    { ...created, category: created.category || category }
                ]);
                setNewOpen(false);
                setSelectedPhotos([]);
                showToast('Servicio creado exitosamente', 'success');
            })
            .catch(e =>
                showToast(
                    e.message ||
                    'No se pudo crear el servicio',
                    'danger'
                )
            );
    };

    const removePhoto = (index) => {
        setSelectedPhotos(prev => prev.filter((_, idx) => idx !== index));
    };

    const saveEditedService = (formData) => {
        const categoryId = normalizeNumericValue(formData.categoryId);
        const category = categories.find(c => c.id === categoryId);
        const formDataToSend = new FormData();
        if (formData.title != null) formDataToSend.append('title', formData.title || '');
        if (formData.description != null) formDataToSend.append('description', formData.description || '');

        const priceHourly = normalizeNumericValue(formData.priceHourly);
        if (priceHourly !== null) formDataToSend.append('priceHourly', String(priceHourly));

        if (categoryId !== null) formDataToSend.append('categoryId', String(categoryId));

        const averageDurationMinutes = normalizeNumericValue(formData.averageDurationMinutes);
        if (averageDurationMinutes !== null) formDataToSend.append('averageDurationMinutes', String(averageDurationMinutes));

        const operationRadiusKm = normalizeNumericValue(formData.operationRadiusKm);
        if (operationRadiusKm !== null) formDataToSend.append('operationRadiusKm', String(operationRadiusKm));

        const retainedExistingPhotos = selectedPhotos.filter((photo) => typeof photo === 'string');
        const removedPhotos = (editedService?.photos || []).filter((photo) => !retainedExistingPhotos.includes(photo));
        retainedExistingPhotos.forEach((photo) => formDataToSend.append('existingPhotos', photo));
        removedPhotos.forEach((photo) => formDataToSend.append('removedPhotos', photo));
        selectedPhotos.filter((photo) => photo instanceof File).forEach((photo) => formDataToSend.append('photos', photo));

        serviceApi
            .updateService(editedService.id, formDataToSend, true)
            .then((updated) => {
                const payload = {
                    ...editedService,
                    ...updated,
                    category: category || editedService.category
                };
                setServices(prev =>
                    prev.map(s =>
                        s.id === payload.id
                            ? payload
                            : s
                    )
                );
                setEditOpen(false);
                setEditedService(null);
                setSelectedPhotos([]);
                showToast(
                    'Servicio actualizado exitosamente',
                    'success'
                );
            })
            .catch(e =>
                showToast(
                    e.message ||
                    'No se pudo actualizar el servicio',
                    'danger'
                )
            );
    }

    const deleteService = (service) => () => {
        serviceApi
            .deleteService(service.id)
            .then(() => {
                setServices(prev => prev.filter(s => s.id !== service.id));
                showToast('Servicio eliminado', 'danger');
            })
            .catch(e =>
                showToast(
                    e.message ||
                    'No se pudo eliminar el servicio',
                    'danger'
                )
            );
    };

    return (
        <DashboardLayout sections={OFFERER_NAV} avatar="CM">
            <div className="page-head">
                <div className="ph" style={{ margin: 0 }}><h1>Mis servicios</h1><p>Crea y administra tu catálogo de servicios</p></div>
                <button className="btn btn-primary" onClick={() => setNewOpen(true)}><Icon name="plus" size={15} />Nuevo servicio</button>
            </div>

            <div className="g2" style={{ gap: '14px' }}>
                {services.map((s) => (
                    <div className={`card svc-card ${!s.active ? 'svc-inactive' : ''}`} key={s.id}>
                        <div className="svc-actions">
                            {s.active ? <span className="badge badge-success">Activo</span> : <span className="badge badge-gray">Inactivo</span>}
                            {s.active && (
                                <>
                                    <button className="btn btn-ghost btn-sm" onClick={openEdit(s)} style={{ border: '1px solid var(--c-border)' }}><Icon name="edit" size={13} /></button>
                                    <button className="btn btn-danger btn-sm" onClick={deleteService(s)}><Icon name="trash" size={13} /></button>
                                </>
                            )}
                        </div>
                        <ServiceImage
                            src={s.photos && s.photos.length > 0 ? getApiImageUrl(s.photos[0]) : null}
                            alt={s.title}
                            style={{ width: '100%', height: '138px', objectFit: 'cover', borderRadius: '12px', marginBottom: '10px' }}
                            fallback={<div className={`svc-ico ${!s.active ? 'svc-ico-off' : ''}`}><Icon name="wrench" size={22} /></div>}
                        />
                        <div className="svc-name">{s.title}</div>
                        <span className={`badge ${s.active ? 'badge-primary' : 'badge-gray'}`} style={{ marginBottom: '10px' }}>{s.category?.name}</span>
                        <div className="svc-desc">{s.description}</div>
                        {s.active && (
                            <div className="svc-stats">
                                <span><Icon name="dollar" size={12} />desde {s.priceHourly}/hr</span>
                                <span><Icon name="clock" size={12} />{s.averageDurationMinutes} min</span>
                                {s.operationRadiusKm && (
                                    <span><Icon name="mapPin" size={12} />{s.operationRadiusKm} km</span>
                                )}
                            </div>
                        )}
                        <div className="divider" />
                        <button className="btn btn-outline btn-sm btn-full" onClick={() => navigate(`/offerer/services/${s.id}`)}>
                            <Icon name="search" size={13} />Ver detalle
                        </button>
                    </div>
                ))}
            </div>

            {newOpen && (
                <ServiceModal
                    onClose={closeModal}
                    title="Nuevo servicio"
                    sub="Crea una nueva oferta de servicio para tu catálogo"
                    onSave={createService}
                    categories={categories}
                    editedService={null}
                    selectedPhotos={selectedPhotos}
                    setSelectedPhotos={setSelectedPhotos}
                    removePhoto={removePhoto}
                />
            )}
            {editOpen && editedService && (
                <ServiceModal
                    onClose={closeModal}
                    edit
                    title="Editar servicio"
                    sub="Actualiza la información de tu servicio"
                    onSave={saveEditedService}
                    categories={categories}
                    editedService={editedService}
                    selectedPhotos={selectedPhotos}
                    setSelectedPhotos={setSelectedPhotos}
                    removePhoto={removePhoto}
                />
            )}
            <ToastContainer toasts={toasts} />
        </DashboardLayout>
    );
}

export default OffererServicesPage;
