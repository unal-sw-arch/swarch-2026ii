import { Link } from 'react-router-dom';

import "./CategoryCard.css";

export const CategoryCard = ({ id, name, icon }) => {
    return (
        <Link
            to={id ? `/services?categoryId=${id}` : "/services"}
            className="cat-card">
            <div className="cat-ico">
                <svg
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8">
                    {icon}
                </svg>
            </div>
            <div className="cat-name">
                {name}
            </div>
        </Link>
    );
};