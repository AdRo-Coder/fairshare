import {API_BASE} from "./config.js";

import(API_BASE)

export async function createGroup(name, description) {
    const response = await fetch(`${API_BASE}/groups`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ name, description })
    });

    if (response.status === 400) {
        return { errors: await response.json() };
    }
    if (!response.ok) {
        throw new Error(`Failed to create group: ${response.status}`);
    }
    return { group: await response.json() };
}

export async function getGroups() {
    const response = await fetch(`${API_BASE}/groups`, { credentials: 'include' });
    if (!response.ok) throw new Error(`Failed to load groups: ${response.status}`);
    return response.json();
}

export async function getGroup(id) {
    const response = await fetch(`${API_BASE}/groups/${id}`, { credentials: 'include' });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Failed to load group: ${response.status}`);
    return response.json();
}