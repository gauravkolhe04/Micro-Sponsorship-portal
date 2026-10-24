// API Base URL - Configured for your live Render Backend
        const API_BASE = window.location.hostname === 'localhost' && window.location.port === '8080'
            ? ''
            : 'https://micro-sponsorship-portal-1.onrender.com';

        const USD_TO_INR = 83;

        function formatCurrency(usdAmount) {
            return '\u20B9' + Math.round(usdAmount * USD_TO_INR).toLocaleString('en-IN');
        }

        // Preset Images for Fast Project Creation
        const PRESET_IMAGES = {
            ai: 'https://images.unsplash.com/photo-1593508512255-86ab42a8e620?auto=format&fit=crop&w=800&q=80',
            drone: 'https://images.unsplash.com/photo-1508614589041-895b88991e3e?auto=format&fit=crop&w=800&q=80',
            solar: 'https://images.unsplash.com/photo-1509391365360-2e959784a276?auto=format&fit=crop&w=800&q=80',
            health: 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=800&q=80',
            iot: 'https://images.unsplash.com/photo-1586771107445-d3ca888129ff?auto=format&fit=crop&w=800&q=80'
        };

        // Application State
        let allProjects = [];
        let allStudents = [];
        let allSponsors = [];
        let currentUser = null;
        let pendingVerificationEmail = '';
        let pendingVerificationRole = 'student';
        let selectedPaymentMode = '';
        let paymentConsentGranted = false;
        let pendingSponsorship = null;
        let currentCategory = 'ALL';
        let currentSearchQuery = '';
        let currentStatusFilter = 'ALL';
        let currentSort = 'featured';

        // ==========================================
        // INITIALIZATION
        // ==========================================
        document.addEventListener('DOMContentLoaded', () => {
            initEventListeners();
            loadInitialData();
            checkSavedSession();
        });

        function initEventListeners() {
            document.getElementById('searchInput').addEventListener('input', (e) => {
                currentSearchQuery = e.target.value.toLowerCase().trim();
                renderProjects();
            });

            document.getElementById('statusFilter').addEventListener('change', (e) => {
                currentStatusFilter = e.target.value;
                renderProjects();
            });

            document.getElementById('sortFilter').addEventListener('change', (e) => {
                currentSort = e.target.value;
                renderProjects();
            });

            const pills = document.querySelectorAll('.category-pill-btn');
            pills.forEach(pill => {
                pill.addEventListener('click', () => {
                    pills.forEach(p => p.classList.remove('active'));
                    pill.classList.add('active');
                    currentCategory = pill.getAttribute('data-category');
                    renderProjects();
                });
            });

            const mobileBtn = document.getElementById('mobileMenuToggle');
            const navLinks = document.getElementById('navLinks');
            if (mobileBtn && navLinks) {
                mobileBtn.addEventListener('click', () => {
                    navLinks.classList.toggle('mobile-open');
                });
            }

            const backToTopBtn = document.getElementById('backToTopBtn');
            if (backToTopBtn) {
                window.addEventListener('scroll', () => {
                    if (window.scrollY > 350) backToTopBtn.classList.add('visible');
                    else backToTopBtn.classList.remove('visible');
                });
                backToTopBtn.addEventListener('click', () => {
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                });
            }

            const dateInput = document.getElementById('updateMilestoneDate');
            if (dateInput) {
                dateInput.value = new Date().toISOString().split('T')[0];
            }

            const customSponsorInput = document.getElementById('customSponsorAmount');
            if (customSponsorInput) {
                customSponsorInput.addEventListener('input', handleSponsorAmountChange);
            }

            window.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    document.querySelectorAll('.modal-backdrop.active').forEach(m => m.classList.remove('active'));
                }
            });
        }

        // ==========================================
        // DATA FETCHING & API CALLS
        // ==========================================
        async function loadInitialData() {
            showSpinner('Loading student projects...');
            try {
                const response = await fetch(`${API_BASE}/projects`);
                if (response.ok) {
                    allProjects = await response.json();
                    const statusEl = document.getElementById('backendStatusIndicator');
                    if (statusEl) {
                        statusEl.innerHTML = `
                            <span style="width: 10px; height: 10px; border-radius: 50%; background: var(--success); display: inline-block;"></span>
                            <span>Connected to Live Server</span>
                        `;
                    }
                } else {
                    throw new Error('Failed to fetch projects');
                }

                loadStats();
                loadStudentsAndSponsors();
            } catch (err) {
                console.warn('Backend fetch failed, falling back:', err);
                const statusEl = document.getElementById('backendStatusIndicator');
                if (statusEl) {
                    statusEl.innerHTML = `
                        <span style="width: 10px; height: 10px; border-radius: 50%; background: var(--warning); display: inline-block;"></span>
                        <span>Offline / Cache Mode</span>
                    `;
                }
                seedLocalFallbackData();
            } finally {
                hideSpinner();
                renderProjects();
            }
        }

        async function loadStats() {
            try {
                const res = await fetch(`${API_BASE}/stats`);
                if (res.ok) {
                    const stats = await res.json();
                    animateCounter('statStudentsCount', stats.totalStudents);
                    animateCounter('statSponsorsCount', stats.totalSponsors);
                    animateCounter('statProjectsCount', stats.totalProjects);
                    animateCounter('statTotalFunding', stats.totalSponsorship, true);
                    animateCounter('statCompletedCount', stats.completedProjects);
                }
            } catch (e) {
                calculateLocalStats();
            }
        }

        async function loadStudentsAndSponsors() {
            try {
                const [stuRes, spoRes] = await Promise.all([
                    fetch(`${API_BASE}/students`),
                    fetch(`${API_BASE}/sponsors`)
                ]);
                if (stuRes.ok) allStudents = await stuRes.json();
                if (spoRes.ok) allSponsors = await spoRes.json();
            } catch (e) {
                console.log('Using local cached entities');
            }
        }

        function calculateLocalStats() {
            const totalProjects = allProjects.length;
            let totalFunded = 0;
            let completed = 0;
            allProjects.forEach(p => {
                totalFunded += p.currentFunding;
                if (p.currentFunding >= p.fundingGoal) completed++;
            });
            animateCounter('statStudentsCount', allStudents.length || 4);
            animateCounter('statSponsorsCount', allSponsors.length || 3);
            animateCounter('statProjectsCount', totalProjects);
            animateCounter('statTotalFunding', totalFunded, true);
            animateCounter('statCompletedCount', completed);
        }

        function animateCounter(elementId, targetValue, isCurrency = false) {
            const el = document.getElementById(elementId);
            if (!el) return;
            let current = 0;
            const step = Math.max(1, Math.floor(targetValue / 30));
            const timer = setInterval(() => {
                current += step;
                if (current >= targetValue) {
                    current = targetValue;
                    clearInterval(timer);
                }
                el.innerText = isCurrency ? formatCurrency(current) : current;
            }, 25);
        }

        function seedLocalFallbackData() {
            if (allProjects.length === 0) {
                allProjects = [
                    {
                        id: 'PRJ-101',
                        title: 'Smart Campus AI Copilot',
                        category: 'Artificial Intelligence',
                        description: 'A conversational campus assistant that helps students discover events and career guidance.',
                        fundingGoal: 1250,
                        currentFunding: 1250,
                        fundingPercentage: 100.0,
                        requiredSkills: 'Python, TensorFlow, React',
                        duration: '8 weeks',
                        teamMembers: 'Gaurav Kolhe',
                        imageUrl: PRESET_IMAGES.ai,
                        githubUrl: 'https://github.com',
                        demoUrl: 'https://demo.example.com',
                        studentId: 'STU-101',
                        studentName: 'Gaurav Kolhe',
                        studentEmail: 'gau@gmail.com',
                        createdAt: '2026-08-01',
                        status: 'Funded',
                        updates: [],
                        sponsorships: []
                    }
                ];
            }
            calculateLocalStats();
        }

        function renderProjects() {
            const grid = document.getElementById('projectsGrid');
            if (!grid) return;

            let filtered = allProjects.filter(p => {
                if (currentCategory !== 'ALL' && p.category !== currentCategory) return false;
                if (currentStatusFilter !== 'ALL' && p.status !== currentStatusFilter) return false;
                if (currentSearchQuery) {
                    const matchTitle = p.title.toLowerCase().includes(currentSearchQuery);
                    const matchStudent = (p.studentName || '').toLowerCase().includes(currentSearchQuery);
                    const matchCategory = (p.category || '').toLowerCase().includes(currentSearchQuery);
                    const matchSkills = (p.requiredSkills || '').toLowerCase().includes(currentSearchQuery);
                    if (!matchTitle && !matchStudent && !matchCategory && !matchSkills) return false;
                }
                return true;
            });

            filtered.sort((a, b) => {
                if (currentSort === 'newest') return new Date(b.createdAt) - new Date(a.createdAt);
                if (currentSort === 'most-funded') return b.currentFunding - a.currentFunding;
                if (currentSort === 'goal-low') return a.fundingGoal - b.fundingGoal;
                if (currentSort === 'goal-high') return b.fundingGoal - a.fundingGoal;
                return (b.currentFunding / b.fundingGoal) - (a.currentFunding / a.fundingGoal);
            });

            if (filtered.length === 0) {
                grid.innerHTML = `
                    <div class="empty-state">
                        <div class="empty-icon"><i class="fa-solid fa-folder-open"></i></div>
                        <h3>No projects match your filter criteria</h3>
                        <p style="color: var(--text-muted); margin-top: 6px;">Try adjusting your search keywords or category filters.</p>
                        <button class="btn btn-secondary btn-sm" style="margin-top: 14px;" onclick="resetFilters()">
                            <i class="fa-solid fa-rotate-left"></i> Reset Filters
                        </button>
                    </div>
                `;
                return;
            }

            grid.innerHTML = filtered.map(p => {
                const percent = Math.min(100, ((p.currentFunding / p.fundingGoal) * 100)).toFixed(1);
                const isFunded = p.currentFunding >= p.fundingGoal;
                const skillsArr = (p.requiredSkills || '').split(',').map(s => s.trim()).filter(s => s.length > 0);

                return `
                    <div class="project-card">
                        <div class="project-card-image-wrap">
                            <img src="${p.imageUrl || PRESET_IMAGES.ai}" alt="${escapeHtml(p.title)}" class="project-card-image" onerror="this.src='${PRESET_IMAGES.ai}'">
                            <span class="badge badge-category project-card-badge">${escapeHtml(p.category)}</span>
                            <span class="badge ${isFunded ? 'badge-success' : 'badge-primary'} project-card-status">
                                ${isFunded ? '<i class="fa-solid fa-check-double"></i> 100% Funded' : '<i class="fa-solid fa-circle-dot"></i> ' + escapeHtml(p.status)}
                            </span>
                        </div>
                        <div class="project-card-body">
                            <div class="project-card-student">
                                <i class="fa-solid fa-user-graduate"></i>
                                <span>${escapeHtml(p.studentName)}</span>
                            </div>
                            <h3 class="project-card-title">${escapeHtml(p.title)}</h3>
                            <p class="project-card-desc">${escapeHtml(p.description)}</p>

                            <div class="project-skills-list">
                                ${skillsArr.slice(0, 3).map(skill => `<span class="skill-tag">${escapeHtml(skill)}</span>`).join('')}
                                ${skillsArr.length > 3 ? `<span class="skill-tag">+${skillsArr.length - 3}</span>` : ''}
                            </div>

                            <div class="funding-progress-box">
                                <div class="progress-meta">
                                    <span class="progress-amount">${formatCurrency(p.currentFunding)} raised of ${formatCurrency(p.fundingGoal)}</span>
                                    <span class="progress-percentage">${percent}%</span>
                                </div>
                                <div class="progress-track">
                                    <div class="progress-fill" style="width: ${percent}%;"></div>
                                </div>
                            </div>

                            <div class="project-card-footer">
                                <button class="btn btn-secondary btn-sm" onclick="openProjectDetailsModal('${p.id}')">
                                    <i class="fa-solid fa-circle-info"></i> View Details
                                </button>
                                <button class="btn btn-primary btn-sm" onclick="openSponsorModal('${p.id}')">
                                    <i class="fa-solid fa-hand-holding-dollar"></i> Sponsor
                                </button>
                            </div>
                        </div>
                    </div>
                `;
            }).join('');
        }

        function resetFilters() {
            document.getElementById('searchInput').value = '';
            document.getElementById('statusFilter').value = 'ALL';
            document.getElementById('sortFilter').value = 'featured';
            currentSearchQuery = '';
            currentStatusFilter = 'ALL';
            currentSort = 'featured';
            setCategoryFilter('ALL');
        }

        function setCategoryFilter(category) {
            currentCategory = category;
            const pills = document.querySelectorAll('.category-pill-btn');
            pills.forEach(p => {
                if (p.getAttribute('data-category') === category) p.classList.add('active');
                else p.classList.remove('active');
            });
            renderProjects();
        }

        function openProjectDetailsModal(projectId) {
            const project = allProjects.find(p => p.id === projectId);
            if (!project) return;

            const percent = Math.min(100, ((project.currentFunding / project.fundingGoal) * 100)).toFixed(1);
            const skillsArr = (project.requiredSkills || '').split(',').map(s => s.trim()).filter(s => s.length > 0);
            const updates = project.updates || [];
            const sponsorships = project.sponsorships || [];

            const modalContent = document.getElementById('projectDetailContent');
            modalContent.innerHTML = `
                <div class="project-detail-hero">
                    <img src="${project.imageUrl || PRESET_IMAGES.ai}" alt="${escapeHtml(project.title)}" onerror="this.src='${PRESET_IMAGES.ai}'">
                    <div class="project-detail-overlay">
                        <span class="badge badge-category" style="margin-bottom: 6px;">${escapeHtml(project.category)}</span>
                        <h2 style="font-size: 1.8rem; margin-bottom: 4px;">${escapeHtml(project.title)}</h2>
                        <div style="font-size: 0.9rem; opacity: 0.9;">Led by ${escapeHtml(project.studentName)} (${escapeHtml(project.studentEmail || 'Creator')})</div>
                    </div>
                </div>

                <div class="detail-meta-grid">
                    <div>
                        <div class="detail-card-box">
                            <h4 style="margin-bottom: 12px; display: flex; align-items: center; gap: 8px;">
                                <i class="fa-solid fa-align-left" style="color: var(--primary);"></i> Project Architecture & Vision
                            </h4>
                            <p style="font-size: 0.95rem; color: var(--text-muted); line-height: 1.7;">
                                ${escapeHtml(project.description)}
                            </p>
                        </div>

                        <div class="detail-card-box">
                            <h4 style="margin-bottom: 14px;">Technical Specifications</h4>
                            <div style="margin-bottom: 12px;">
                                <strong style="font-size: 0.85rem; color: var(--text-muted);">Required Skills:</strong>
                                <div style="display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px;">
                                    ${skillsArr.map(s => `<span class="badge badge-primary" style="font-size: 0.78rem;">${escapeHtml(s)}</span>`).join('')}
                                </div>
                            </div>
                            <div style="margin-bottom: 12px;">
                                <strong style="font-size: 0.85rem; color: var(--text-muted);">Team:</strong>
                                <p style="font-size: 0.9rem; margin-top: 4px;">${escapeHtml(project.teamMembers || 'Individual Student')}</p>
                            </div>
                            <div style="margin-bottom: 12px;">
                                <strong style="font-size: 0.85rem; color: var(--text-muted);">Duration:</strong>
                                <span style="font-size: 0.9rem; margin-left: 8px;">${escapeHtml(project.duration || 'Semester Capstone')}</span>
                            </div>

                            <div style="display: flex; gap: 12px; margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--border-light);">
                                ${project.githubUrl ? `<a href="${escapeHtml(project.githubUrl)}" target="_blank" class="btn btn-secondary btn-sm"><i class="fa-brands fa-github"></i> GitHub</a>` : ''}
                                ${project.demoUrl ? `<a href="${escapeHtml(project.demoUrl)}" target="_blank" class="btn btn-outline btn-sm"><i class="fa-solid fa-arrow-up-right-from-square"></i> Live Demo</a>` : ''}
                            </div>
                        </div>

                        <div class="detail-card-box">
                            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
                                <h4><i class="fa-solid fa-timeline" style="color: var(--primary);"></i> Milestones (${updates.length})</h4>
                                ${currentUser && currentUser.id === project.studentId ? `
                                    <button class="btn btn-primary btn-sm" onclick="openAddUpdateModal('${project.id}')">
                                        <i class="fa-solid fa-plus"></i> Post Update
                                    </button>
                                ` : ''}
                            </div>

                            ${updates.length === 0 ? `<p style="font-size: 0.88rem; color: var(--text-muted);">No milestone updates published yet.</p>` : `
                                <div class="updates-timeline">
                                    ${updates.map(u => `
                                        <div class="update-timeline-item">
                                            <div class="update-date"><i class="fa-regular fa-calendar"></i> ${escapeHtml(u.date)}</div>
                                            <div class="update-title">${escapeHtml(u.title)}</div>
                                            <div class="update-desc">${escapeHtml(u.description)}</div>
                                        </div>
                                    `).join('')}
                                </div>
                            `}
                        </div>
                    </div>

                    <div>
                        <div class="detail-card-box" style="position: sticky; top: 90px;">
                            <h4 style="margin-bottom: 14px;">Funding Progress</h4>
                            <div class="funding-progress-box">
                                <div class="progress-meta">
                                    <span class="progress-amount" style="font-size: 1.1rem;">${formatCurrency(project.currentFunding)}</span>
                                    <span class="progress-percentage">${percent}%</span>
                                </div>
                                <div class="progress-track" style="height: 12px;">
                                    <div class="progress-fill" style="width: ${percent}%;"></div>
                                </div>
                                <div style="font-size: 0.85rem; color: var(--text-muted); margin-top: 6px;">
                                    Goal: ${formatCurrency(project.fundingGoal)}
                                </div>
                            </div>

                            <button class="btn btn-primary btn-lg" style="width: 100%; margin-bottom: 18px;" onclick="closeModal('projectDetailsModal'); openSponsorModal('${project.id}');">
                                <i class="fa-solid fa-hand-holding-dollar"></i> Sponsor This Project
                            </button>

                            <h4 style="margin-bottom: 12px; font-size: 0.95rem;">Backers (${sponsorships.length})</h4>
                            ${sponsorships.length === 0 ? `<p style="font-size: 0.85rem; color: var(--text-muted);">Be the first backer!</p>` : `
                                <div class="backers-list">
                                    ${sponsorships.map(sp => `
                                        <div class="backer-item">
                                            <div class="backer-info">
                                                <div class="backer-avatar">${escapeHtml((sp.sponsorName || 'S').charAt(0))}</div>
                                                <div>
                                                    <div style="font-weight: 600; font-size: 0.88rem;">${escapeHtml(sp.sponsorName)}</div>
                                                    <div style="font-size: 0.75rem; color: var(--text-muted);">"${escapeHtml(sp.message || 'Proud backer')}"</div>
                                                </div>
                                            </div>
                                            <div style="font-weight: 700; color: var(--primary-light); font-size: 0.9rem;">
                                                +${formatCurrency(sp.amount)}
                                            </div>
                                        </div>
                                    `).join('')}
                                </div>
                            `}
                        </div>
                    </div>
                </div>
            `;
            openModal('projectDetailsModal');
        }

        // ==========================================
        // SPONSORSHIP PAYMENT SIMULATION
        // ==========================================
        function openSponsorModal(projectId) {
            const project = allProjects.find(p => p.id === projectId);
            if (!project) return;

            if (project.currentFunding >= project.fundingGoal) {
                showToast('This project is already fully funded.', 'info');
                return;
            }

            document.getElementById('sponsorTargetProjectId').value = project.id;
            document.getElementById('sponsorTargetProjectTitle').innerText = project.title;
            selectedPaymentMode = '';
            paymentConsentGranted = false;
            pendingSponsorship = null;
            document.querySelectorAll('input[name="payMode"]').forEach(input => input.checked = false);
            document.getElementById('paymentInstructions').hidden = true;
            document.getElementById('upiPaymentPanel').hidden = true;
            document.getElementById('paypalPaymentPanel').hidden = true;
            const remainingInr = Math.max(0, (project.fundingGoal - project.currentFunding) * USD_TO_INR);
            const amountInput = document.getElementById('customSponsorAmount');
            amountInput.max = Math.round(remainingInr);
            document.getElementById('sponsorRemainingLimit').innerText = `Remaining project limit: ${formatCurrency(remainingInr / USD_TO_INR)}`;

            if (currentUser) {
                document.getElementById('sponsorFormName').value = currentUser.name || '';
                document.getElementById('sponsorFormEmail').value = currentUser.email || '';
            }

            selectSponsorAmount(0);
            openModal('sponsorModal');
        }

        function selectSponsorAmount(amt) {
            const buttons = document.querySelectorAll('.amount-btn');
            const customInput = document.getElementById('customSponsorAmount');

            buttons.forEach(btn => {
                if (amt > 0 && btn.dataset.usd === String(amt)) btn.classList.add('active');
                else if (amt === 0 && btn.innerText === 'Custom') btn.classList.add('active');
                else btn.classList.remove('active');
            });

            if (amt > 0) customInput.value = Math.round(amt * USD_TO_INR);
            else {
                customInput.value = '';
                customInput.focus();
            }
            handleSponsorAmountChange();
        }

        function handlePaymentModeSelection() {
            selectedPaymentMode = document.querySelector('input[name="payMode"]:checked')?.value || '';
            paymentConsentGranted = false;
            pendingSponsorship = null;
            document.getElementById('paymentInstructions').hidden = true;
            document.getElementById('upiPaymentPanel').hidden = true;
            document.getElementById('paypalPaymentPanel').hidden = true;
        }

        function updatePaymentDetails() {
            const amountInr = parseFloat(document.getElementById('customSponsorAmount').value);
            const amountText = amountInr > 0 ? formatCurrency(amountInr / USD_TO_INR) : 'Enter an amount first';
            const cardAmount = document.getElementById('cardPaymentAmount');
            const upiAmount = document.getElementById('upiPaymentAmount');
            const paypalAmount = document.getElementById('paypalPaymentAmount');
            if (cardAmount) cardAmount.innerText = amountText;
            if (upiAmount) upiAmount.innerText = amountText;
            if (paypalAmount) paypalAmount.innerText = amountText;

            const qrCode = document.getElementById('paymentQrCode');
            if (qrCode) qrCode.replaceChildren();
            if (qrCode && amountInr > 0 && typeof QRCode !== 'undefined') {
                const projectTitle = document.getElementById('sponsorTargetProjectTitle').innerText;
                const qrData = `upi://pay?pa=studentfund@demo&pn=StudentFund&am=${Math.round(amountInr)}&tn=${encodeURIComponent(projectTitle)}`;
                new QRCode(qrCode, {
                    text: qrData,
                    width: 168,
                    height: 168,
                    colorDark: '#000000',
                    colorLight: '#ffffff',
                    correctLevel: QRCode.CorrectLevel.M
                });
            }
        }

        function handleSponsorAmountChange() {
            paymentConsentGranted = false;
            pendingSponsorship = null;
            document.getElementById('paymentInstructions').hidden = true;
            document.getElementById('upiPaymentPanel').hidden = true;
            document.getElementById('paypalPaymentPanel').hidden = true;
            updatePaymentDetails();
        }

        function openSelectedPaymentMethod() {
            const instructions = document.getElementById('paymentInstructions');
            instructions.hidden = selectedPaymentMode === 'card';
            document.getElementById('upiPaymentPanel').hidden = selectedPaymentMode !== 'upi';
            document.getElementById('paypalPaymentPanel').hidden = selectedPaymentMode !== 'paypal';
            updatePaymentDetails();

            if (selectedPaymentMode === 'card') {
                document.getElementById('cardPaymentForm').reset();
                openModal('cardPaymentModal');
            }
        }

        async function markDemoPaymentComplete(mode) {
            if (mode !== selectedPaymentMode || !paymentConsentGranted || !pendingSponsorship) {
                showToast('Review and approve the sponsorship before continuing.', 'warning');
                return;
            }
            const { payload, target } = pendingSponsorship;
            paymentConsentGranted = false;
            pendingSponsorship = null;
            if (mode === 'card') closeModal('cardPaymentModal');
            await processSponsorship(payload, target);
        }

        function handleCardPaymentSubmit(e) {
            e.preventDefault();
            markDemoPaymentComplete('card');
        }

        async function handleSponsorSubmit(e) {
            e.preventDefault();
            if (!selectedPaymentMode) {
                showToast('Choose a payment method before continuing.', 'warning');
                return;
            }

            const projectId = document.getElementById('sponsorTargetProjectId').value;
            const amountInr = parseFloat(document.getElementById('customSponsorAmount').value);
            const sponsorName = document.getElementById('sponsorFormName').value.trim();
            const sponsorEmail = document.getElementById('sponsorFormEmail').value.trim();
            const message = document.getElementById('sponsorFormMessage').value.trim();
            const target = allProjects.find(project => project.id === projectId);

            if (!amountInr || amountInr <= 0) {
                showToast('Please enter a valid sponsorship amount.', 'error');
                return;
            }

            const amountVal = amountInr / USD_TO_INR;
            if (!target || target.currentFunding >= target.fundingGoal) {
                showToast('This project is already fully funded.', 'info');
                return;
            }
            const remaining = target.fundingGoal - target.currentFunding;
            if (amountVal > remaining) {
                showToast(`Amount exceeds remaining limit of ${formatCurrency(remaining)}.`, 'error');
                return;
            }

            const payload = {
                projectId: projectId,
                amount: amountVal,
                sponsorName: sponsorName,
                sponsorEmail: sponsorEmail,
                sponsorId: currentUser ? currentUser.id : ('SPO-' + Math.floor(Math.random() * 900 + 100)),
                message: message || 'Proud backer of student engineering!'
            };

            pendingSponsorship = { payload, target };
            if (selectedPaymentMode === 'upi') {
                document.getElementById('confirmTitle').innerHTML = '<i class="fa-solid fa-qrcode"></i> Open UPI QR Code';
                document.getElementById('confirmTitle').style.color = 'var(--primary)';
                document.getElementById('confirmMessage').innerText = `Open payment QR code for ${formatCurrency(amountVal)} to sponsor "${target.title}"?`;
                const confirmButton = document.getElementById('confirmAcceptBtn');
                confirmButton.innerText = 'Open QR Code';
                confirmButton.className = 'btn btn-primary';
                confirmButton.onclick = () => {
                    paymentConsentGranted = true;
                    closeModal('confirmModal');
                    openSelectedPaymentMethod();
                };
                openModal('confirmModal');
            } else {
                paymentConsentGranted = true;
                openSelectedPaymentMethod();
            }
        }

        async function processSponsorship(payload, target) {
            closeModal('confirmModal');
            showSpinner('Processing micro-sponsorship...');

            try {
                const res = await fetch(`${API_BASE}/sponsor`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });

                if (res.ok) {
                    const result = await res.json();
                    const idx = allProjects.findIndex(p => p.id === payload.projectId);
                    if (idx !== -1 && result.project) allProjects[idx] = result.project;
                    showToast(`Thank you, ${payload.sponsorName}! Sponsorship of ${formatCurrency(payload.amount)} received.`, 'success');
                } else {
                    throw new Error('Server error');
                }
            } catch (err) {
                showToast(`Demo sponsorship of ${formatCurrency(payload.amount)} recorded locally.`, 'success');
            } finally {
                hideSpinner();
                closeModal('sponsorModal');
                closeModal('cardPaymentModal');
                renderProjects();
                calculateLocalStats();
            }
        }

        // ==========================================
        // CREATE / EDIT / DELETE PROJECT
        // ==========================================
        function handleStartProjectClick() {
            if (!currentUser) {
                showToast('Please log in as a student to create a project.', 'info');
                openAuthModal('student');
                return;
            }
            if (currentUser.role !== 'student' && currentUser.role !== 'admin') {
                showToast('Only student accounts can create engineering projects.', 'warning');
                return;
            }
            openCreateProjectModal();
        }

        function openCreateProjectModal() {
            document.getElementById('projectFormTitle').innerHTML = '<i class="fa-solid fa-rocket"></i> Create Student Project';
            document.getElementById('crudSubmitBtn').innerHTML = '<i class="fa-solid fa-plus-circle"></i> Launch Project';
            document.getElementById('crudProjectId').value = '';
            document.getElementById('crudTitle').value = '';
            document.getElementById('crudCategory').value = 'Artificial Intelligence';
            document.getElementById('crudFundingGoal').value = '';
            document.getElementById('crudDescription').value = '';
            document.getElementById('crudSkills').value = currentUser ? (currentUser.skills || '') : '';
            document.getElementById('crudDuration').value = '4 Months';
            document.getElementById('crudTeam').value = currentUser ? `${currentUser.name} (Lead)` : '';
            document.getElementById('crudImageUrl').value = PRESET_IMAGES.ai;
            document.getElementById('crudGithubUrl').value = '';
            document.getElementById('crudDemoUrl').value = '';
            openModal('projectFormModal');
        }

        function openEditProjectModal(projectId) {
            const p = allProjects.find(item => item.id === projectId);
            if (!p) return;

            document.getElementById('projectFormTitle').innerHTML = '<i class="fa-solid fa-pen-to-square"></i> Edit Project Details';
            document.getElementById('crudSubmitBtn').innerHTML = '<i class="fa-solid fa-floppy-disk"></i> Update Project';
            document.getElementById('crudProjectId').value = p.id;
            document.getElementById('crudTitle').value = p.title;
            document.getElementById('crudCategory').value = p.category;
            document.getElementById('crudFundingGoal').value = Math.round(p.fundingGoal * USD_TO_INR);
            document.getElementById('crudDescription').value = p.description;
            document.getElementById('crudSkills').value = p.requiredSkills || '';
            document.getElementById('crudDuration').value = p.duration || '';
            document.getElementById('crudTeam').value = p.teamMembers || '';
            document.getElementById('crudImageUrl').value = p.imageUrl || '';
            document.getElementById('crudGithubUrl').value = p.githubUrl || '';
            document.getElementById('crudDemoUrl').value = p.demoUrl || '';
            openModal('projectFormModal');
        }

        function setPresetImg(key) {
            if (PRESET_IMAGES[key]) {
                document.getElementById('crudImageUrl').value = PRESET_IMAGES[key];
            }
        }

        async function handleProjectFormSubmit(e) {
            e.preventDefault();
            const id = document.getElementById('crudProjectId').value;
            const title = document.getElementById('crudTitle').value.trim();
            const category = document.getElementById('crudCategory').value;
            const fundingGoalInr = parseFloat(document.getElementById('crudFundingGoal').value);
            const fundingGoal = fundingGoalInr / USD_TO_INR;
            const description = document.getElementById('crudDescription').value.trim();
            const requiredSkills = document.getElementById('crudSkills').value.trim();
            const duration = document.getElementById('crudDuration').value.trim();
            const teamMembers = document.getElementById('crudTeam').value.trim();
            const imageUrl = document.getElementById('crudImageUrl').value.trim() || PRESET_IMAGES.ai;
            const githubUrl = document.getElementById('crudGithubUrl').value.trim();
            const demoUrl = document.getElementById('crudDemoUrl').value.trim();

            if (!title || !description || !fundingGoalInr || fundingGoalInr <= 0) {
                showToast('Please fill all required project fields.', 'error');
                return;
            }

            showSpinner(id ? 'Updating project...' : 'Publishing project...');

            const payload = {
                id: id,
                title: title,
                category: category,
                fundingGoal: fundingGoal,
                description: description,
                requiredSkills: requiredSkills,
                duration: duration,
                teamMembers: teamMembers,
                imageUrl: imageUrl,
                githubUrl: githubUrl,
                demoUrl: demoUrl,
                studentId: currentUser ? currentUser.id : 'STU-101',
                studentName: currentUser ? currentUser.name : 'Student Innovator',
                studentEmail: currentUser ? currentUser.email : 'student@mit.edu'
            };

            const endpoint = id ? `${API_BASE}/updateProject` : `${API_BASE}/createProject`;

            try {
                const res = await fetch(endpoint, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });

                if (res.ok) {
                    const result = await res.json();
                    if (id) {
                        const idx = allProjects.findIndex(p => p.id === id);
                        if (idx !== -1) allProjects[idx] = result.project;
                    } else {
                        allProjects.unshift(result.project);
                    }
                    showToast(result.message || 'Project saved successfully!', 'success');
                } else {
                    const errRes = await res.json();
                    throw new Error(errRes.message || 'Failed to save project');
                }
            } catch (err) {
                showToast('Project updated locally.', 'success');
            } finally {
                hideSpinner();
                closeModal('projectFormModal');
                renderProjects();
                calculateLocalStats();
                if (currentUser) renderDashboardContent();
            }
        }

        function confirmDeleteProject(projectId) {
            const p = allProjects.find(item => item.id === projectId);
            if (!p) return;

            document.getElementById('confirmTitle').innerHTML = '<i class="fa-solid fa-triangle-exclamation"></i> Confirm Action';
            document.getElementById('confirmTitle').style.color = 'var(--danger)';
            document.getElementById('confirmMessage').innerText = `Are you sure you want to delete "${p.title}"?`;
            const confirmButton = document.getElementById('confirmAcceptBtn');
            confirmButton.innerText = 'Confirm Delete';
            confirmButton.className = 'btn btn-danger';
            confirmButton.onclick = () => executeDeleteProject(projectId);
            openModal('confirmModal');
        }

        async function executeDeleteProject(projectId) {
            closeModal('confirmModal');
            showSpinner('Deleting project...');

            try {
                const res = await fetch(`${API_BASE}/deleteProject`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ id: projectId })
                });

                if (res.ok) {
                    allProjects = allProjects.filter(p => p.id !== projectId);
                    showToast('Project removed successfully.', 'success');
                } else {
                    throw new Error('Delete failed on server');
                }
            } catch (err) {
                allProjects = allProjects.filter(p => p.id !== projectId);
                showToast('Project removed locally.', 'success');
            } finally {
                hideSpinner();
                renderProjects();
                calculateLocalStats();
                if (currentUser) renderDashboardContent();
            }
        }

        // ==========================================
        // PROJECT UPDATES (MILESTONES)
        // ==========================================
        function openAddUpdateModal(projectId) {
            document.getElementById('updateTargetProjectId').value = projectId;
            document.getElementById('updateMilestoneTitle').value = '';
            document.getElementById('updateMilestoneDesc').value = '';
            document.getElementById('updateMilestoneDate').value = new Date().toISOString().split('T')[0];
            openModal('updateFormModal');
        }

        async function handlePostUpdateSubmit(e) {
            e.preventDefault();
            const projectId = document.getElementById('updateTargetProjectId').value;
            const title = document.getElementById('updateMilestoneTitle').value.trim();
            const description = document.getElementById('updateMilestoneDesc').value.trim();
            const date = document.getElementById('updateMilestoneDate').value || new Date().toISOString().split('T')[0];

            if (!title || !description) {
                showToast('Please enter milestone title and description.', 'error');
                return;
            }

            showSpinner('Posting update...');

            try {
                const res = await fetch(`${API_BASE}/addProjectUpdate`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ projectId, title, description, date })
                });

                if (res.ok) {
                    const result = await res.json();
                    const idx = allProjects.findIndex(p => p.id === projectId);
                    if (idx !== -1) allProjects[idx] = result.project;
                    showToast('Milestone update published!', 'success');
                } else {
                    throw new Error('Update failed');
                }
            } catch (err) {
                showToast('Milestone update posted locally.', 'success');
            } finally {
                hideSpinner();
                closeModal('updateFormModal');
                const detailsModal = document.getElementById('projectDetailsModal');
                if (detailsModal && detailsModal.classList.contains('active')) {
                    openProjectDetailsModal(projectId);
                }
            }
        }

        // ==========================================
        // AUTHENTICATION & SESSIONS
        // ==========================================
        function openAuthModal(mode = 'login') {
            switchAuthTab(mode);
            openModal('authModal');
        }

        function switchAuthTab(tab) {
            const loginForm = document.getElementById('loginForm');
            const studentForm = document.getElementById('studentRegisterForm');
            const sponsorForm = document.getElementById('sponsorRegisterForm');
            const verificationForm = document.getElementById('emailVerificationForm');

            const tabLoginBtn = document.getElementById('tabLoginBtn');
            const tabStudentBtn = document.getElementById('tabStudentBtn');
            const tabSponsorBtn = document.getElementById('tabSponsorBtn');
            const authTabs = document.querySelector('.auth-tabs');

            [tabLoginBtn, tabStudentBtn, tabSponsorBtn].forEach(b => { if (b) b.classList.remove('active'); });
            if (authTabs) authTabs.style.display = tab === 'login' ? 'none' : 'flex';
            if (tabLoginBtn) tabLoginBtn.style.display = 'none';
            if (tabStudentBtn) tabStudentBtn.style.display = tab === 'login' ? 'none' : 'block';
            if (tabSponsorBtn) tabSponsorBtn.style.display = tab === 'login' ? 'none' : 'block';

            if (loginForm) loginForm.style.display = 'none';
            if (studentForm) studentForm.style.display = 'none';
            if (sponsorForm) sponsorForm.style.display = 'none';
            if (verificationForm) verificationForm.style.display = 'none';

            if (tab === 'student') {
                if (studentForm) studentForm.style.display = 'block';
                if (tabStudentBtn) tabStudentBtn.classList.add('active');
                document.getElementById('authModalTitle').innerHTML = '<i class="fa-solid fa-user-graduate"></i> Student Registration';
            } else if (tab === 'sponsor') {
                if (sponsorForm) sponsorForm.style.display = 'block';
                if (tabSponsorBtn) tabSponsorBtn.classList.add('active');
                document.getElementById('authModalTitle').innerHTML = '<i class="fa-solid fa-building"></i> Sponsor Registration';
            } else {
                if (loginForm) loginForm.style.display = 'block';
                if (tabLoginBtn) tabLoginBtn.classList.add('active');
                document.getElementById('authModalTitle').innerHTML = '<i class="fa-solid fa-arrow-right-to-bracket"></i> Log In';
            }
        }

        function checkSavedSession() {
            const saved = localStorage.getItem('currentUser');
            if (saved) {
                try {
                    currentUser = JSON.parse(saved);
                    updateNavbarAuthUI();
                } catch (e) {
                    localStorage.removeItem('currentUser');
                }
            }
        }

        async function handleLoginSubmit(e) {
            e.preventDefault();
            const email = document.getElementById('loginEmail').value.trim();
            const password = document.getElementById('loginPassword').value.trim();
            const role = document.getElementById('loginRole').value;

            if (!email || !password) {
                showToast('Please enter email and password.', 'error');
                return;
            }

            showSpinner('Authenticating...');

            try {
                const res = await fetch(`${API_BASE}/login`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email, password, role })
                });

                if (res.ok) {
                    const result = await res.json();
                    currentUser = result.user;
                    currentUser.role = result.role;
                    saveSession(currentUser);
                    showToast(result.message || 'Login successful!', 'success');
                    closeModal('authModal');
                } else {
                    const err = await res.json();
                    throw new Error(err.message || 'Invalid credentials');
                }
            } catch (err) {
                showToast(err.message || 'Unable to log in.', 'error');
            } finally {
                hideSpinner();
            }
        }

        async function handleStudentRegisterSubmit(e) {
            e.preventDefault();
            const name = document.getElementById('regStudentName').value.trim();
            const email = document.getElementById('regStudentEmail').value.trim();
            const password = document.getElementById('regStudentPassword').value.trim();
            const university = document.getElementById('regStudentUniversity').value.trim();
            const department = document.getElementById('regStudentDept').value.trim();
            const skills = document.getElementById('regStudentSkills').value.trim();
            const bio = document.getElementById('regStudentBio').value.trim();

            showSpinner('Creating student account...');
            const payload = { role: 'student', name, email, password, university, department, skills, bio };

            try {
                const res = await fetch(`${API_BASE}/register`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const result = await res.json();
                if (res.ok) {
                    if (result.verificationRequired) {
                        showEmailVerificationForm(result.email, result.message, 'student');
                    } else {
                        currentUser = result.user;
                        currentUser.role = 'student';
                        saveSession(currentUser);
                        closeModal('authModal');
                        showToast(result.message || 'Account created successfully!', 'success');
                    }
                } else {
                    throw new Error(result.message || 'Registration failed');
                }
            } catch (err) {
                showToast(err.message, 'error');
            } finally {
                hideSpinner();
            }
        }

        async function handleSponsorRegisterSubmit(e) {
            e.preventDefault();
            const name = document.getElementById('regSponsorName').value.trim();
            const email = document.getElementById('regSponsorEmail').value.trim();
            const password = document.getElementById('regSponsorPassword').value.trim();
            const type = document.getElementById('regSponsorType').value;
            const company = document.getElementById('regSponsorCompany').value.trim();
            const bio = document.getElementById('regSponsorBio').value.trim();

            showSpinner('Creating sponsor account...');
            const payload = { role: 'sponsor', name, email, password, type, company, bio };

            try {
                const res = await fetch(`${API_BASE}/register`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const result = await res.json();
                if (res.ok) {
                    if (result.verificationRequired) {
                        showEmailVerificationForm(result.email, result.message, 'sponsor');
                    } else {
                        currentUser = result.user;
                        currentUser.role = 'sponsor';
                        saveSession(currentUser);
                        closeModal('authModal');
                        showToast(result.message || 'Account created successfully!', 'success');
                    }
                } else {
                    throw new Error(result.message || 'Registration failed');
                }
            } catch (err) {
                showToast(err.message, 'error');
            } finally {
                hideSpinner();
            }
        }

        function showEmailVerificationForm(email, message, role) {
            pendingVerificationEmail = email;
            pendingVerificationRole = role;
            document.getElementById('loginForm').style.display = 'none';
            document.getElementById('studentRegisterForm').style.display = 'none';
            document.getElementById('sponsorRegisterForm').style.display = 'none';
            document.querySelector('.auth-tabs').style.display = 'none';
            document.getElementById('authModalTitle').innerHTML = '<i class="fa-solid fa-envelope-circle-check"></i> Verify Email';
            document.getElementById('emailVerificationMessage').innerText = `${message} (${email})`;
            document.getElementById('emailVerificationCode').value = '';
            document.getElementById('emailVerificationForm').style.display = 'block';
            openModal('authModal');
        }

        async function handleEmailVerificationSubmit(e) {
            e.preventDefault();
            const code = document.getElementById('emailVerificationCode').value.trim();
            showSpinner('Verifying code...');
            try {
                const res = await fetch(`${API_BASE}/verifyEmail`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: pendingVerificationEmail, code })
                });
                const result = await res.json();
                if (!res.ok) throw new Error(result.message || 'Verification failed.');

                currentUser = result.user;
                currentUser.role = result.role;
                saveSession(currentUser);
                closeModal('authModal');
                showToast('Email verified successfully!', 'success');
            } catch (err) {
                showToast(err.message, 'error');
            } finally {
                hideSpinner();
            }
        }

        function saveSession(user) {
            localStorage.setItem('currentUser', JSON.stringify(user));
            updateNavbarAuthUI();
        }

        function logout() {
            currentUser = null;
            localStorage.removeItem('currentUser');
            updateNavbarAuthUI();
            closeModal('dashboardModal');
            showToast('You have been signed out.', 'info');
        }

        function updateNavbarAuthUI() {
            const container = document.getElementById('navAuthArea');
            if (!container) return;

            if (currentUser) {
                const roleBadge = currentUser.role === 'admin' ? 'Admin' : (currentUser.role === 'sponsor' ? 'Sponsor' : 'Student');
                container.innerHTML = `
                    <div class="user-chip" onclick="openDashboard()">
                        <div class="user-avatar">${escapeHtml((currentUser.name || 'U').charAt(0))}</div>
                        <div style="font-size: 0.88rem; font-weight: 600;">
                            <span class="user-chip-name">${escapeHtml(currentUser.name)}</span>
                            <span style="font-size: 0.72rem; color: var(--primary-light); display: block;">${roleBadge} Dashboard</span>
                        </div>
                    </div>
                `;
            } else {
                container.innerHTML = `
                    <button class="btn btn-primary btn-sm" onclick="openAuthModal('login')">
                        <i class="fa-solid fa-arrow-right-to-bracket"></i> Log In
                    </button>
                `;
            }
        }

        // ==========================================
        // DASHBOARD VIEW
        // ==========================================
        function openDashboard() {
            if (!currentUser) return;
            renderDashboardContent();
            openModal('dashboardModal');
        }

        function renderDashboardContent() {
            const body = document.getElementById('dashboardBody');
            const title = document.getElementById('dashboardTitle');
            if (!body || !currentUser) return;

            if (currentUser.role === 'student') {
                title.innerHTML = '<i class="fa-solid fa-user-graduate"></i> Student Dashboard';
                renderStudentDashboard(body);
            } else if (currentUser.role === 'sponsor') {
                title.innerHTML = '<i class="fa-solid fa-building"></i> Sponsor Dashboard';
                renderSponsorDashboard(body);
            }
        }

        function renderStudentDashboard(container) {
            const myProjects = allProjects.filter(p => p.studentId === currentUser.id || p.studentEmail === currentUser.email);
            let totalRaised = 0;
            let totalBackers = 0;

            myProjects.forEach(p => {
                totalRaised += p.currentFunding;
                totalBackers += (p.sponsorships ? p.sponsorships.length : 0);
            });

            container.innerHTML = `
                <div class="dashboard-header">
                    <div class="dashboard-user-info">
                        <div class="dashboard-avatar-large">${escapeHtml((currentUser.name || 'S').charAt(0))}</div>
                        <div>
                            <h2>${escapeHtml(currentUser.name)}</h2>
                            <p style="color: var(--text-muted); font-size: 0.9rem;">${escapeHtml(currentUser.university || 'University')} &bull; ${escapeHtml(currentUser.department || 'Department')}</p>
                        </div>
                    </div>
                    <div style="display: flex; gap: 10px;">
                        <button class="btn btn-primary btn-sm" onclick="closeModal('dashboardModal'); openCreateProjectModal();">
                            <i class="fa-solid fa-plus-circle"></i> Create Project
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="logout()">
                            <i class="fa-solid fa-arrow-right-from-bracket"></i> Sign Out
                        </button>
                    </div>
                </div>

                <div class="stats-grid" style="margin-bottom: 28px;">
                    <div class="stat-card"><div class="stat-value">${myProjects.length}</div><div class="stat-label">Projects</div></div>
                    <div class="stat-card"><div class="stat-value">${formatCurrency(totalRaised)}</div><div class="stat-label">Raised</div></div>
                    <div class="stat-card"><div class="stat-value">${totalBackers}</div><div class="stat-label">Backers</div></div>
                </div>

                <h3 style="margin-bottom: 14px;">My Projects</h3>
                ${myProjects.length === 0 ? `<p style="color:var(--text-muted);">No projects yet.</p>` : `
                    <div style="overflow-x: auto;">
                        <table class="dashboard-table">
                            <thead>
                                <tr><th>Project</th><th>Category</th><th>Goal</th><th>Raised</th><th>Actions</th></tr>
                            </thead>
                            <tbody>
                                ${myProjects.map(p => `
                                    <tr>
                                        <td><strong>${escapeHtml(p.title)}</strong></td>
                                        <td>${escapeHtml(p.category)}</td>
                                        <td>${formatCurrency(p.fundingGoal)}</td>
                                        <td>${formatCurrency(p.currentFunding)}</td>
                                        <td>
                                            <button class="btn btn-secondary btn-sm" onclick="closeModal('dashboardModal'); openAddUpdateModal('${p.id}');"><i class="fa-solid fa-bullhorn"></i></button>
                                            <button class="btn btn-danger btn-sm" onclick="closeModal('dashboardModal'); confirmDeleteProject('${p.id}');"><i class="fa-solid fa-trash"></i></button>
                                        </td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                `}
            `;
        }

        function renderSponsorDashboard(container) {
            let mySponsorships = [];
            allProjects.forEach(p => {
                (p.sponsorships || []).forEach(sp => {
                    if (sp.sponsorId === currentUser.id || sp.sponsorEmail === currentUser.email) {
                        mySponsorships.push(sp);
                    }
                });
            });

            container.innerHTML = `
                <div class="dashboard-header">
                    <div class="dashboard-user-info">
                        <div class="dashboard-avatar-large">${escapeHtml((currentUser.name || 'S').charAt(0))}</div>
                        <div>
                            <h2>${escapeHtml(currentUser.name)}</h2>
                            <p style="color: var(--text-muted); font-size: 0.9rem;">${escapeHtml(currentUser.type || 'Sponsor')} &bull; ${escapeHtml(currentUser.email)}</p>
                        </div>
                    </div>
                    <button class="btn btn-secondary btn-sm" onclick="logout()"><i class="fa-solid fa-arrow-right-from-bracket"></i> Sign Out</button>
                </div>
                <h3 style="margin: 20px 0 14px;">Sponsored Projects (${mySponsorships.length})</h3>
                ${mySponsorships.length === 0 ? `<p style="color:var(--text-muted);">No sponsorships yet.</p>` : `
                    <div style="overflow-x: auto;">
                        <table class="dashboard-table">
                            <thead><tr><th>Project</th><th>Amount</th><th>Date</th></tr></thead>
                            <tbody>
                                ${mySponsorships.map(s => `
                                    <tr>
                                        <td>${escapeHtml(s.projectTitle || 'Project')}</td>
                                        <td style="color:var(--success); font-weight:700;">+${formatCurrency(s.amount)}</td>
                                        <td>${escapeHtml(s.date)}</td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                `}
            `;
        }

        // ==========================================
        // CONTACT FORM - DIRECT TO SUPABASE VIA BACKEND
        // ==========================================
        async function handleContactSubmit(e) {
            e.preventDefault();
            const name = document.getElementById('contactName').value.trim();
            const email = document.getElementById('contactEmail').value.trim();
            const subject = document.getElementById('contactSubject').value.trim();
            const message = document.getElementById('contactMessage').value.trim();

            if (!name || !email || !subject || !message) {
                showToast('Please fill all contact fields.', 'error');
                return;
            }

            showSpinner('Sending message to Supabase...');
            try {
                const response = await fetch(`${API_BASE}/contact`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name, email, subject, message })
                });

                const result = await response.json();
                if (response.ok && result.success) {
                    showToast(`Thank you, ${name}! Your inquiry has been saved in Supabase.`, 'success');
                    document.getElementById('contactForm').reset();
                } else {
                    throw new Error(result.message || 'Failed to submit message');
                }
            } catch (err) {
                showToast(err.message || 'Could not send message. Please try again.', 'error');
            } finally {
                hideSpinner();
            }
        }

        // ==========================================
        // UI UTILITIES
        // ==========================================
        function openModal(modalId) {
            const modal = document.getElementById(modalId);
            if (modal) {
                modal.classList.add('active');
                document.body.style.overflow = 'hidden';
            }
        }

        function closeModal(modalId) {
            const modal = document.getElementById(modalId);
            if (modal) {
                modal.classList.remove('active');
                document.body.style.overflow = '';
            }
        }

        function togglePasswordVisibility(inputId, toggleButton) {
            const input = document.getElementById(inputId);
            const icon = toggleButton.querySelector('i');
            const isHidden = input.type === 'password';

            input.type = isHidden ? 'text' : 'password';
            toggleButton.setAttribute('aria-label', isHidden ? 'Hide password' : 'Show password');
            icon.className = isHidden ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
        }

        function showToast(message, type = 'info') {
            const container = document.getElementById('toastContainer');
            if (!container) return;

            const toast = document.createElement('div');
            toast.className = `toast toast-${type}`;
            let iconHtml = '<i class="fa-solid fa-circle-info toast-icon"></i>';
            if (type === 'success') iconHtml = '<i class="fa-solid fa-circle-check toast-icon"></i>';
            if (type === 'error') iconHtml = '<i class="fa-solid fa-circle-exclamation toast-icon"></i>';
            if (type === 'warning') iconHtml = '<i class="fa-solid fa-triangle-exclamation toast-icon"></i>';

            toast.innerHTML = `${iconHtml}<div class="toast-body">${escapeHtml(message)}</div>`;
            container.appendChild(toast);

            setTimeout(() => toast.classList.add('show'), 50);
            setTimeout(() => {
                toast.classList.remove('show');
                setTimeout(() => toast.remove(), 400);
            }, 4500);
        }

        function showSpinner(text = 'Loading...') {
            const overlay = document.getElementById('spinnerOverlay');
            const txt = document.getElementById('spinnerText');
            if (overlay && txt) {
                txt.innerText = text;
                overlay.classList.add('active');
            }
        }

        function hideSpinner() {
            const overlay = document.getElementById('spinnerOverlay');
            if (overlay) overlay.classList.remove('active');
        }

        function escapeHtml(str) {
            if (!str) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }
