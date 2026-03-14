package com.ecom.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.ai.AIRecommendationService;
import com.ecom.model.Category;
import com.ecom.model.Product;
import com.ecom.model.UserDtls;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.ProductService;
import com.ecom.service.UserService;
import com.ecom.service.impl.S3ImageService;
import com.ecom.util.CommonUtil;

import io.micrometer.common.util.StringUtils;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

@Controller
public class HomeController {

    @Autowired private CategoryService categoryService;
    @Autowired private ProductService productService;
    @Autowired private UserService userService;
    @Autowired private CommonUtil commonUtil;
    @Autowired private CartService cartService;
    @Autowired private AIRecommendationService aiRecommendationService;
    @Autowired private S3ImageService s3ImageService;

    @ModelAttribute
    public void getUserDetails(Principal p, Model m) {
        if (p != null) {
            String email = p.getName();
            UserDtls userDtls = userService.getUserByEmail(email);
            m.addAttribute("user", userDtls);
            Integer countCart = cartService.getCountCart(userDtls.getId());
            m.addAttribute("countCart", countCart);
        }
        List<Category> allActiveCategory = categoryService.getAllActiveCategory();
        m.addAttribute("categorys", allActiveCategory);
    }

    // ── Homepage ──
    @GetMapping("/")
    public String index(Model m) {
        List<Category> allActiveCategory = categoryService.getAllActiveCategory()
                .stream().sorted((c1, c2) -> c2.getId().compareTo(c1.getId()))
                .limit(6).toList();
        List<Product> allActiveProducts = productService.getAllActiveProducts("")
                .stream().sorted((p1, p2) -> p2.getId().compareTo(p1.getId()))
                .limit(8).toList();
        m.addAttribute("category", allActiveCategory);
        m.addAttribute("products", allActiveProducts);
        return "index";
    }

    // ── Product detail with AI recommendations ──
    @GetMapping("/product/{id}")
    public String product(@PathVariable int id, Model m) {
        Product product = productService.getProductById(id);
        m.addAttribute("product", product);

        // Get similar products in same category
        List<Product> similarProducts = productService.getAllActiveProducts(product.getCategory())
                .stream().filter(p -> !p.getId().equals(product.getId()))
                .limit(4).toList();
        m.addAttribute("similarProducts", similarProducts);

        // AI recommendation text
        try {
            String aiRecommendation = aiRecommendationService.getProductRecommendations(
                    product.getCategory(), product.getTitle(), similarProducts);
            m.addAttribute("aiRecommendation", aiRecommendation);
        } catch (Exception e) {
            m.addAttribute("aiRecommendation", "Customers who viewed this also loved these products!");
        }

        return "view_product";
    }

    // ── Products listing with search ──
    @GetMapping("/products")
    public String products(Model m,
            @RequestParam(value = "category", defaultValue = "") String category,
            @RequestParam(name = "pageNo", defaultValue = "0") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "12") Integer pageSize,
            @RequestParam(defaultValue = "") String ch) {

        List<Category> categories = categoryService.getAllActiveCategory();
        m.addAttribute("paramValue", category);
        m.addAttribute("categories", categories);

        Page<Product> page;
        if (StringUtils.isEmpty(ch)) {
            page = productService.getAllActiveProductPagination(pageNo, pageSize, category);
        } else {
            page = productService.searchActiveProductPagination(pageNo, pageSize, category, ch);
            // AI search suggestion
            try {
                String suggestion = aiRecommendationService.getSearchSuggestion(ch);
                m.addAttribute("searchSuggestion", suggestion);
            } catch (Exception ignored) {}
        }

        m.addAttribute("products", page.getContent());
        m.addAttribute("productsSize", page.getContent().size());
        m.addAttribute("pageNo", page.getNumber());
        m.addAttribute("pageSize", pageSize);
        m.addAttribute("totalElements", page.getTotalElements());
        m.addAttribute("totalPages", page.getTotalPages());
        m.addAttribute("isFirst", page.isFirst());
        m.addAttribute("isLast", page.isLast());
        m.addAttribute("ch", ch);

        return "product";
    }

    // ── Login / Register ──
    @GetMapping("/signin")  public String login()    { return "login"; }
    @GetMapping("/register") public String register() { return "register"; }

    // ── Register user ──
    @PostMapping("/saveUser")
    public String saveUser(@ModelAttribute UserDtls user,
                           @RequestParam("img") MultipartFile file,
                           HttpSession session) throws IOException {

        if (userService.existsEmail(user.getEmail())) {
            session.setAttribute("errorMsg", "Email already exists");
            return "redirect:/register";
        }

        // Upload profile image to S3
        String imageName = s3ImageService.uploadImage(file, "profile_img");
        user.setProfileImage(imageName);

        UserDtls saved = userService.saveUser(user);
        if (!ObjectUtils.isEmpty(saved)) {
            session.setAttribute("succMsg", "Registered successfully! Please login.");
        } else {
            session.setAttribute("errorMsg", "Something went wrong on server");
        }
        return "redirect:/register";
    }

    // ── Search ──
    @GetMapping("/search")
    public String searchProduct(@RequestParam String ch, Model m) {
        List<Product> searchProducts = productService.searchProduct(ch);
        m.addAttribute("products", searchProducts);
        m.addAttribute("categories", categoryService.getAllActiveCategory());
        return "product";
    }

    // ── Forgot Password ──
    @GetMapping("/forgot-password")
    public String showForgotPassword() { return "forgot_password"; }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email,
                                         HttpSession session,
                                         HttpServletRequest request)
            throws UnsupportedEncodingException, MessagingException {

        UserDtls userByEmail = userService.getUserByEmail(email);
        if (ObjectUtils.isEmpty(userByEmail)) {
            session.setAttribute("errorMsg", "Invalid email");
        } else {
            String resetToken = UUID.randomUUID().toString();
            userService.updateUserResetToken(email, resetToken);
            String url = CommonUtil.generateUrl(request) + "/reset-password?token=" + resetToken;
            Boolean sent = commonUtil.sendMail(url, email);
            session.setAttribute(sent ? "succMsg" : "errorMsg",
                    sent ? "Password reset link sent to your email" : "Email could not be sent");
        }
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam String token, HttpSession session, Model m) {
        UserDtls userByToken = userService.getUserByToken(token);
        if (userByToken == null) {
            m.addAttribute("msg", "Your link is invalid or expired!");
            return "message";
        }
        m.addAttribute("token", token);
        return "reset_password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                 @RequestParam String password,
                                 Model m) {
        UserDtls userByToken = userService.getUserByToken(token);
        if (userByToken == null) {
            m.addAttribute("errorMsg", "Your link is invalid or expired!");
            return "message";
        }
        userByToken.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
        userByToken.setResetToken(null);
        userService.updateUser(userByToken);
        m.addAttribute("msg", "Password changed successfully! Please login.");
        return "message";
    }
}
