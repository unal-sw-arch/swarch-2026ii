import { useEffect, useState, useRef } from 'react';
import { AppNavbar } from '../AppNavbar/AppNavbar';
import { Sidebar } from '../Sidebar/Sidebar';
import { notificationApi, chatApi } from '../../api';
import { useToast } from '../../hooks/useToast';

export function DashboardLayout({ sections, avatar, avatarSrc, navLinks = [], children }) {
    const { showToast } = useToast();
    const [unreadCount, setUnreadCount] = useState(0);
    const prevCountRef = useRef(0);

    useEffect(() => {
        const check = () => {
            notificationApi.getNotifications({ read: false, size: 1 })
                .then(page => {
                    const total = page.totalElements;
                    setUnreadCount(total);
                    if (total > prevCountRef.current) {
                        const diff = total - prevCountRef.current;
                        showToast(
                            `Tienes ${diff} notificación${diff > 1 ? 'es' : ''} nueva${diff > 1 ? 's' : ''}`,
                            'info',
                            '/notifications'
                        );
                    }
                    prevCountRef.current = total;
                })
                .catch(() => {});
        };
        check();
        const id = setInterval(check, 30000);
        return () => clearInterval(id);
    }, [showToast]);

    // Mensajes sin leer del chat, para el contador de «Mensajes» en el menú. Si el chat responde 401 (aún sin
    // el emisor de tokens que acepta) se deja de consultar en vez de insistir cada 30 s.
    const [chatUnread, setChatUnread] = useState(0);
    const hasChatEntry = sections.some((s) => s.items.some((it) => it.to === '/messages'));
    useEffect(() => {
        if (!hasChatEntry) return undefined;
        let stopped = false;
        const check = () => {
            chatApi.listConversations({ limit: 50 })
                .then((page) => setChatUnread((page.items || []).reduce((n, c) => n + (c.unread_count || 0), 0)))
                .catch((e) => {
                    if (e.status === 401) {
                        stopped = true;
                        clearInterval(id);
                    }
                });
        };
        check();
        const id = setInterval(() => !stopped && check(), 30000);
        return () => clearInterval(id);
    }, [hasChatEntry]);

    const sectionsWithBadges = chatUnread > 0
        ? sections.map((s) => ({
            ...s,
            items: s.items.map((it) => (it.to === '/messages' ? { ...it, badge: chatUnread > 99 ? '99+' : chatUnread } : it)),
        }))
        : sections;

    return (
        <>
            <AppNavbar avatar={avatar} avatarSrc={avatarSrc} links={navLinks} unreadCount={unreadCount} showBurger />
            <div className="layout">
                <Sidebar sections={sectionsWithBadges} />
                <main className="main">{children}</main>
            </div>
        </>
    );
}
