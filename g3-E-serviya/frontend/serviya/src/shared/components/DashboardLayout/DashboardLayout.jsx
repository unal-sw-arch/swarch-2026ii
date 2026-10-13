import { useEffect, useState, useRef } from 'react';
import { AppNavbar } from '../AppNavbar/AppNavbar';
import { Sidebar } from '../Sidebar/Sidebar';
import { notificationApi } from '../../api';
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

    return (
        <>
            <AppNavbar avatar={avatar} avatarSrc={avatarSrc} links={navLinks} unreadCount={unreadCount} showBurger />
            <div className="layout">
                <Sidebar sections={sections} />
                <main className="main">{children}</main>
            </div>
        </>
    );
}
